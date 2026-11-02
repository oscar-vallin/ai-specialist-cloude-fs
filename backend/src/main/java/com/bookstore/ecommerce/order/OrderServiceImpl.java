package com.bookstore.ecommerce.order;

import com.bookstore.ecommerce.catalog.CatalogService;
import com.bookstore.ecommerce.catalog.CatalogService.ProductInfo;
import com.bookstore.ecommerce.catalog.InventoryService;
import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.Caller;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.common.PageResponse;
import com.bookstore.ecommerce.config.AppProperties;
import com.bookstore.ecommerce.member.MemberAddressService;
import com.bookstore.ecommerce.member.MemberDtos.Address;
import com.bookstore.ecommerce.member.GiftPointService;
import com.bookstore.ecommerce.order.CartRepository.CartItemRow;
import com.bookstore.ecommerce.order.CartRepository.CartRow;
import com.bookstore.ecommerce.order.CartRepository.CouponRow;
import com.bookstore.ecommerce.order.OrderDtos.BuyAgainLine;
import com.bookstore.ecommerce.order.OrderDtos.BuyAgainResult;
import com.bookstore.ecommerce.order.OrderDtos.CancelOrderRequest;
import com.bookstore.ecommerce.order.OrderDtos.CheckoutQuote;
import com.bookstore.ecommerce.order.OrderDtos.CheckoutRequest;
import com.bookstore.ecommerce.order.OrderDtos.Order;
import com.bookstore.ecommerce.order.OrderDtos.OrderItem;
import com.bookstore.ecommerce.order.OrderDtos.OrderReturn;
import com.bookstore.ecommerce.order.OrderDtos.OrderSummary;
import com.bookstore.ecommerce.order.OrderDtos.PlaceOrderRequest;
import com.bookstore.ecommerce.order.OrderDtos.QuoteItem;
import com.bookstore.ecommerce.order.OrderDtos.ReturnOrderRequest;
import com.bookstore.ecommerce.order.OrderDtos.ShippingAddress;
import com.bookstore.ecommerce.order.OrderRepository.OrderItemRow;
import com.bookstore.ecommerce.order.OrderRepository.OrderReturnRow;
import com.bookstore.ecommerce.order.OrderRepository.OrderRow;
import com.bookstore.ecommerce.order.OrderRepository.OrderSummaryRow;
import com.bookstore.ecommerce.payment.PaymentService;
import com.bookstore.ecommerce.shipping.ShipmentService;
import com.bookstore.ecommerce.shipping.ShippingRateService;
import com.bookstore.ecommerce.store.StoreService;
import com.bookstore.ecommerce.store.StoreService.StorePolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CartService cartService;
    private final CatalogService catalogService;
    private final InventoryService inventoryService;
    private final StoreService storeService;
    private final ShippingRateService shippingRateService;
    private final MemberAddressService addressService;
    private final GiftPointService giftPointService;
    private final ShipmentService shipmentService;
    private final PaymentService paymentService;
    private final AppProperties properties;
    private final Clock clock;

    OrderServiceImpl(
            OrderRepository orderRepository,
            CartRepository cartRepository,
            CartService cartService,
            CatalogService catalogService,
            InventoryService inventoryService,
            StoreService storeService,
            ShippingRateService shippingRateService,
            MemberAddressService addressService,
            GiftPointService giftPointService,
            ShipmentService shipmentService,
            @Lazy PaymentService paymentService,
            AppProperties properties,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.cartService = cartService;
        this.catalogService = catalogService;
        this.inventoryService = inventoryService;
        this.storeService = storeService;
        this.shippingRateService = shippingRateService;
        this.addressService = addressService;
        this.giftPointService = giftPointService;
        this.shipmentService = shipmentService;
        this.paymentService = paymentService;
        this.properties = properties;
        this.clock = clock;
    }

    public record PlacedOrderResult(Order order, boolean replayed) {
    }

    @Transactional(readOnly = true)
    public CheckoutQuote checkoutQuote(Caller caller, CheckoutRequest request) {
        UUID userId = caller.requireUser();
        Address address = addressService.findOwnAddress(userId, request.addressId())
                .orElseThrow(() -> ApiException.of(ErrorCode.ADDRESS_NOT_FOUND, "Address not found: " + request.addressId()));

        UUID storeId = storeService.resolveStoreId(null);
        CartRow cart = cartRepository.findActiveByUser(userId, storeId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CART_EMPTY, "Cart is empty."));

        List<CartItemRow> items = cartRepository.findItems(cart.id());
        if (items.isEmpty()) {
            throw ApiException.of(ErrorCode.CART_EMPTY, "Cart is empty.");
        }

        ComputedQuote computed = computeQuote(caller, storeId, items, address, request.couponCode(), cart.couponId());
        long pointsBalance = giftPointService.pointsBalance(userId);
        StorePolicy policy = storeService.policy(storeId);
        int pointsRate = policy.giftPointsPerCurrencyUnit();
        long maxPoints = computed.total.multiply(BigDecimal.valueOf(pointsRate))
                .multiply(BigDecimal.valueOf(policy.maxGiftPointsPercent()))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .longValue();
        long maxGiftPointsPayable = Math.min(pointsBalance, maxPoints);

        return new CheckoutQuote(
                properties.currency(),
                computed.quoteItems,
                computed.subtotal,
                computed.taxTotal,
                computed.shippingTotal,
                computed.couponDiscount,
                computed.total,
                computed.couponCode,
                address,
                computed.estimatedDeliveryDate,
                pointsBalance,
                maxGiftPointsPayable,
                OffsetDateTime.now(clock));
    }

    @Transactional
    public PlacedOrderResult placeOrder(Caller caller, String idempotencyKey, PlaceOrderRequest request) {
        UUID userId = caller.requireUser();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.of(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Idempotency-Key header is required.");
        }

        String requestHash = hashPlaceOrderRequest(request);
        Optional<OrderRow> existing = orderRepository.findByIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            OrderRow row = existing.get();
            if (requestHash.equals(row.requestHash())) {
                return new PlacedOrderResult(view(row.id()), true);
            } else {
                throw ApiException.of(ErrorCode.IDEMPOTENCY_KEY_REUSED, "Idempotency key has already been used with different request parameters.");
            }
        }

        Address address = addressService.findOwnAddress(userId, request.addressId())
                .orElseThrow(() -> ApiException.of(ErrorCode.ADDRESS_NOT_FOUND, "Address not found: " + request.addressId()));

        UUID storeId = storeService.resolveStoreId(null);
        CartRow cart = cartRepository.findActiveByUser(userId, storeId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CART_EMPTY, "Cart is empty."));

        List<CartItemRow> items = cartRepository.findItems(cart.id());
        if (items.isEmpty()) {
            throw ApiException.of(ErrorCode.CART_EMPTY, "Cart is empty.");
        }

        ComputedQuote computed = computeQuote(caller, storeId, items, address, request.couponCode(), cart.couponId());

        if (request.expectedTotal() != null && request.expectedTotal().compareTo(computed.total) != 0) {
            throw ApiException.of(ErrorCode.PRICE_CHANGED,
                    "Order total has changed. Expected: " + request.expectedTotal() + ", Actual: " + computed.total);
        }

        Map<UUID, Integer> stockToReserve = new HashMap<>();
        for (ComputedItem ci : computed.computedItems) {
            if (ci.product.physical()) {
                stockToReserve.put(ci.product.id(), ci.quantity);
            }
        }
        if (!stockToReserve.isEmpty()) {
            inventoryService.reserve(stockToReserve);
        }

        String orderNumber = orderRepository.nextOrderNumber();
        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime expiresAt = now.plusMinutes(properties.order().paymentWindowMinutes());

        UUID orderId = orderRepository.insertOrder(
                orderNumber,
                userId,
                storeId,
                cart.id(),
                "PENDING_PAYMENT",
                properties.currency(),
                computed.subtotal,
                computed.taxTotal,
                computed.shippingTotal,
                computed.couponDiscount,
                computed.total,
                computed.couponId,
                address.firstName(),
                address.lastName(),
                address.line1(),
                address.line2(),
                address.city(),
                address.state(),
                address.postalCode(),
                address.country(),
                address.email(),
                address.phoneNumber(),
                expiresAt,
                idempotencyKey,
                requestHash);

        for (ComputedItem ci : computed.computedItems) {
            orderRepository.insertOrderItem(
                    orderId,
                    ci.product.id(),
                    ci.product.title(),
                    ci.product.isbn(),
                    ci.product.price(),
                    ci.quantity,
                    ci.lineTotal,
                    ci.taxAmount,
                    ci.estimatedDeliveryDate);
        }

        orderRepository.insertStatusHistory(orderId, null, "PENDING_PAYMENT", userId, "Order placed");

        if (computed.couponId != null) {
            orderRepository.insertCouponRedemption(computed.couponId, orderId, userId);
        }

        return new PlacedOrderResult(view(orderId), false);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> listOrders(Caller caller, String status, int page, int size, String sort) {
        UUID userId = caller.requireUser();
        String sortDir = "asc".equalsIgnoreCase(sort) || "placedAt,asc".equalsIgnoreCase(sort) ? "asc" : "desc";
        int offset = page * size;
        int total = orderRepository.countUserOrders(userId, status);
        List<OrderSummaryRow> rows = orderRepository.listUserOrders(userId, status, offset, size, sortDir);

        List<OrderSummary> content = rows.stream()
                .map(r -> new OrderSummary(
                        r.id(),
                        r.orderNumber(),
                        r.status(),
                        r.total(),
                        r.currency(),
                        r.placedAt(),
                        r.itemCount(),
                        r.cancelDeadlineAt()))
                .toList();

        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, page, size, total, totalPages);
    }

    @Transactional(readOnly = true)
    public Order getOrder(Caller caller, UUID orderId) {
        UUID userId = caller.requireUser();
        OrderRow row = orderRepository.findById(orderId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));

        if (!caller.isAdmin() && !userId.equals(row.userId())) {
            throw ApiException.of(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId);
        }

        return view(orderId);
    }

    @Transactional
    public Order cancelOrder(Caller caller, UUID orderId, CancelOrderRequest request) {
        UUID userId = caller.requireUser();
        OrderRow row = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));

        if (!caller.isAdmin() && !userId.equals(row.userId())) {
            throw ApiException.of(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId);
        }

        if (!"CONFIRMED".equals(row.status())) {
            throw ApiException.of(ErrorCode.ORDER_INVALID_STATE, "Order cannot be cancelled in status: " + row.status());
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (row.cancelDeadlineAt() != null && now.isAfter(row.cancelDeadlineAt())) {
            throw ApiException.of(ErrorCode.CANCELLATION_WINDOW_EXPIRED, "Cancellation window has expired.");
        }

        List<OrderItemRow> items = orderRepository.findItemsByOrderId(orderId);
        Map<UUID, Integer> physicalQty = getPhysicalQuantities(items);
        if (!physicalQty.isEmpty()) {
            inventoryService.restock(physicalQty);
        }

        String reason = request != null && request.reason() != null ? request.reason() : "Customer cancelled";
        paymentService.refundAllForOrder(orderId, null, reason);
        shipmentService.closeOutbound(orderId);

        orderRepository.cancelOrder(orderId, now, reason);
        orderRepository.insertStatusHistory(orderId, "CONFIRMED", "CANCELLED", userId, reason);

        return view(orderId);
    }

    @Transactional
    public Order returnOrder(Caller caller, UUID orderId, ReturnOrderRequest request) {
        UUID userId = caller.requireUser();
        OrderRow row = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));

        if (!caller.isAdmin() && !userId.equals(row.userId())) {
            throw ApiException.of(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId);
        }

        if (!"DELIVERED".equals(row.status())) {
            throw ApiException.of(ErrorCode.ORDER_INVALID_STATE, "Order cannot be returned in status: " + row.status());
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (row.returnDeadlineAt() != null && now.isAfter(row.returnDeadlineAt())) {
            throw ApiException.of(ErrorCode.RETURN_WINDOW_EXPIRED, "Return window has expired.");
        }

        orderRepository.insertReturnRequest(orderId, request.reason());
        orderRepository.updateOrderStatus(orderId, "RETURN_REQUESTED");
        orderRepository.insertStatusHistory(orderId, "DELIVERED", "RETURN_REQUESTED", userId, request.reason());

        return view(orderId);
    }

    @Transactional
    public BuyAgainResult buyAgain(Caller caller, UUID orderId) {
        UUID userId = caller.requireUser();
        OrderRow orderRow = orderRepository.findById(orderId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));

        if (!caller.isAdmin() && !userId.equals(orderRow.userId())) {
            throw ApiException.of(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId);
        }

        List<OrderItemRow> orderItems = orderRepository.findItemsByOrderId(orderId);
        List<BuyAgainLine> lines = new ArrayList<>();

        for (OrderItemRow oi : orderItems) {
            Optional<ProductInfo> productOpt = catalogService.findVisible(oi.productId(), orderRow.storeId(), caller);
            if (productOpt.isEmpty() || !productOpt.get().active()) {
                lines.add(new BuyAgainLine(
                        oi.productId(),
                        oi.title(),
                        oi.quantity(),
                        0,
                        "SKIPPED",
                        productOpt.isEmpty() ? "PRODUCT_NOT_VISIBLE" : "PRODUCT_ARCHIVED",
                        oi.unitPrice(),
                        null));
                continue;
            }

            ProductInfo p = productOpt.get();
            int requested = oi.quantity();
            int added;
            String outcome;
            String reason = null;

            if (p.physical()) {
                int avail = p.available();
                if (avail <= 0) {
                    outcome = "SKIPPED";
                    reason = "OUT_OF_STOCK";
                    added = 0;
                } else if (avail < requested) {
                    outcome = "ADJUSTED";
                    reason = "OUT_OF_STOCK";
                    added = avail;
                } else {
                    outcome = "ADDED";
                    added = requested;
                }
            } else {
                outcome = "ADDED";
                added = requested;
            }

            if (added > 0) {
                cartService.addCartItem(caller, null, orderRow.storeId(), new OrderDtos.AddCartItemRequest(p.id(), added));
            }

            lines.add(new BuyAgainLine(
                    oi.productId(),
                    p.title(),
                    requested,
                    added,
                    outcome,
                    reason,
                    oi.unitPrice(),
                    p.price()));
        }

        OrderDtos.Cart cart = cartService.getCart(caller, null, orderRow.storeId());
        return new BuyAgainResult(lines, cart);
    }

    // ----- OrderService methods called by other domains -----

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderRef> findRef(UUID orderId) {
        return orderRepository.findById(orderId)
                .map(row -> new OrderRef(row.id(), row.userId(), row.storeId()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PayableOrder lockForPayment(UUID orderId, UUID userId) {
        OrderRow row = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));

        if (!row.userId().equals(userId)) {
            throw ApiException.of(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId);
        }

        return new PayableOrder(
                row.id(),
                row.userId(),
                row.storeId(),
                row.status(),
                row.total(),
                row.currency(),
                row.paymentExpiresAt());
    }

    @Override
    @Transactional(readOnly = true)
    public Order view(UUID orderId) {
        OrderRow row = orderRepository.findById(orderId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));

        List<OrderItemRow> itemRows = orderRepository.findItemsByOrderId(orderId);
        List<OrderItem> items = itemRows.stream()
                .map(i -> new OrderItem(
                        i.id(),
                        i.productId(),
                        i.title(),
                        i.isbn(),
                        i.unitPrice(),
                        i.quantity(),
                        i.lineTotal(),
                        i.taxAmount(),
                        i.estimatedDeliveryDate()))
                .toList();

        String couponCode = null;
        if (row.couponId() != null) {
            couponCode = cartRepository.findCouponById(row.couponId()).map(CouponRow::code).orElse(null);
        }

        ShippingAddress shipAddress = new ShippingAddress(
                row.shipFirstName(),
                row.shipLastName(),
                row.shipLine1(),
                row.shipLine2(),
                row.shipCity(),
                row.shipState(),
                row.shipPostalCode(),
                row.shipCountry(),
                row.shipEmail(),
                row.shipPhone());

        OrderReturn returnRequest = orderRepository.findReturnByOrderId(orderId)
                .map(r -> new OrderReturn(r.id(), r.status(), r.reason(), r.requestedAt(), r.resolvedAt()))
                .orElse(null);

        List<UUID> paymentIds = paymentService.paymentIdsOf(orderId);
        BigDecimal refundedTotal = paymentService.refundedTotal(orderId);
        var shipments = shipmentService.refsOfOrder(orderId);

        return new Order(
                row.id(),
                row.orderNumber(),
                row.status(),
                row.currency(),
                items,
                row.subtotal(),
                row.taxTotal(),
                row.shippingTotal(),
                row.couponDiscount(),
                row.total(),
                refundedTotal != null ? refundedTotal : BigDecimal.ZERO,
                couponCode,
                shipAddress,
                row.placedAt(),
                row.paymentExpiresAt(),
                row.confirmedAt(),
                row.cancelDeadlineAt(),
                row.cancelledAt(),
                row.cancellationReason(),
                row.deliveredAt(),
                row.returnDeadlineAt(),
                returnRequest,
                paymentIds,
                shipments);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmPaid(UUID orderId) {
        OrderRow row = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));

        OffsetDateTime now = OffsetDateTime.now(clock);
        StorePolicy policy = storeService.policy(row.storeId());
        OffsetDateTime cancelDeadline = now.plusHours(policy.cancellationWindowHours());

        orderRepository.confirmOrder(orderId, now, cancelDeadline);
        orderRepository.insertStatusHistory(orderId, "PENDING_PAYMENT", "CONFIRMED", row.userId(), "Payment confirmed");

        List<OrderItemRow> items = orderRepository.findItemsByOrderId(orderId);
        Map<UUID, Integer> physicalQty = getPhysicalQuantities(items);
        if (!physicalQty.isEmpty()) {
            inventoryService.commitSale(physicalQty);
        }

        if (row.cartId() != null) {
            cartRepository.markConverted(row.cartId());
        }

        if (!physicalQty.isEmpty()) {
            LocalDate latestDeliveryDate = items.stream()
                    .map(OrderItemRow::estimatedDeliveryDate)
                    .filter(java.util.Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            shipmentService.createOutbound(orderId, row.shippingTotal(), latestDeliveryDate);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markPaymentFailed(UUID orderId) {
        OrderRow row = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));

        orderRepository.updateOrderStatus(orderId, "PAYMENT_FAILED");
        orderRepository.insertStatusHistory(orderId, "PENDING_PAYMENT", "PAYMENT_FAILED", row.userId(), "Payment failed");

        List<OrderItemRow> items = orderRepository.findItemsByOrderId(orderId);
        Map<UUID, Integer> physicalQty = getPhysicalQuantities(items);
        if (!physicalQty.isEmpty()) {
            inventoryService.release(physicalQty);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markShipped(UUID orderId) {
        orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));
        orderRepository.updateOrderStatus(orderId, "SHIPPED");
        orderRepository.insertStatusHistory(orderId, "CONFIRMED", "SHIPPED", null, "Shipment in transit");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markDelivered(UUID orderId) {
        OrderRow row = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));

        OffsetDateTime now = OffsetDateTime.now(clock);
        StorePolicy policy = storeService.policy(row.storeId());
        OffsetDateTime returnDeadline = now.plusDays(policy.returnWindowDays());

        orderRepository.deliverOrder(orderId, now, returnDeadline);
        orderRepository.insertStatusHistory(orderId, "SHIPPED", "DELIVERED", null, "Shipment delivered");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> pendingReturn(UUID orderId) {
        return orderRepository.findPendingReturn(orderId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void approveReturn(UUID returnId) {
        orderRepository.updateReturnStatus(returnId, "APPROVED", null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeReturn(UUID orderId) {
        orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));

        OffsetDateTime now = OffsetDateTime.now(clock);
        orderRepository.updateReturnStatusByOrderId(orderId, "REFUNDED", now);

        UUID returnId = orderRepository.findReturnByOrderId(orderId).map(OrderReturnRow::id).orElse(null);
        paymentService.refundAllForOrder(orderId, returnId, "Return accepted");

        List<OrderItemRow> items = orderRepository.findItemsByOrderId(orderId);
        Map<UUID, Integer> physicalQty = getPhysicalQuantities(items);
        if (!physicalQty.isEmpty()) {
            inventoryService.restock(physicalQty);
        }

        orderRepository.updateOrderStatus(orderId, "RETURNED");
        orderRepository.insertStatusHistory(orderId, "RETURN_REQUESTED", "RETURNED", null, "Return completed and refunded");
    }

    // ----- Helper calculations -----

    private record ComputedItem(ProductInfo product, int quantity, BigDecimal lineTotal, BigDecimal taxAmount, LocalDate estimatedDeliveryDate) {
    }

    private record ComputedQuote(
            List<QuoteItem> quoteItems,
            List<ComputedItem> computedItems,
            BigDecimal subtotal,
            BigDecimal taxTotal,
            BigDecimal shippingTotal,
            BigDecimal couponDiscount,
            BigDecimal total,
            String couponCode,
            UUID couponId,
            LocalDate estimatedDeliveryDate) {
    }

    private ComputedQuote computeQuote(
            Caller caller,
            UUID storeId,
            List<CartItemRow> itemRows,
            Address address,
            String overrideCouponCode,
            UUID cartCouponId) {
        List<UUID> productIds = itemRows.stream().map(CartItemRow::productId).toList();
        Map<UUID, ProductInfo> products = catalogService.findByIds(productIds).stream()
                .collect(Collectors.toMap(ProductInfo::id, Function.identity()));

        List<ComputedItem> computedItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalWeight = 0;
        boolean hasPhysical = false;

        for (CartItemRow itemRow : itemRows) {
            ProductInfo p = products.get(itemRow.productId());
            if (p == null || !p.active() || !catalogService.isVisible(p.id(), storeId, caller)) {
                throw ApiException.of(ErrorCode.PRODUCT_UNAVAILABLE, "Product unavailable: " + itemRow.productId());
            }

            if (p.physical()) {
                hasPhysical = true;
                if (itemRow.quantity() > p.available()) {
                    throw ApiException.of(ErrorCode.INSUFFICIENT_STOCK, "Insufficient stock for " + p.title());
                }
                if (p.weightGrams() != null) {
                    totalWeight += p.weightGrams() * itemRow.quantity();
                }
            }

            BigDecimal lineTotal = p.price().multiply(BigDecimal.valueOf(itemRow.quantity()));
            subtotal = subtotal.add(lineTotal);
            computedItems.add(new ComputedItem(p, itemRow.quantity(), lineTotal, BigDecimal.ZERO, null));
        }

        // Coupon calculation
        String resolvedCouponCode = overrideCouponCode != null && !overrideCouponCode.isBlank() ? overrideCouponCode : null;
        UUID resolvedCouponId = null;
        if (resolvedCouponCode == null && cartCouponId != null) {
            Optional<CouponRow> cartCoupon = cartRepository.findCouponById(cartCouponId);
            if (cartCoupon.isPresent()) {
                resolvedCouponCode = cartCoupon.get().code();
                resolvedCouponId = cartCoupon.get().id();
            }
        }

        final String couponCodeToUse = resolvedCouponCode;
        UUID couponIdToUse = resolvedCouponId;

        BigDecimal couponDiscount = BigDecimal.ZERO;
        if (couponCodeToUse != null) {
            final String finalCode = couponCodeToUse;
            CouponRow coupon = cartRepository.findCouponByCode(couponCodeToUse, storeId)
                    .orElseThrow(() -> ApiException.of(ErrorCode.COUPON_INVALID, "Coupon is invalid: " + finalCode));
            couponIdToUse = coupon.id();

            OffsetDateTime now = OffsetDateTime.now(clock);
            if (!coupon.active()
                    || (coupon.validFrom() != null && now.isBefore(coupon.validFrom()))
                    || (coupon.validUntil() != null && now.isAfter(coupon.validUntil()))) {
                throw ApiException.of(ErrorCode.COUPON_INVALID, "Coupon is expired or inactive.");
            }

            if (coupon.minOrderAmount() != null && subtotal.compareTo(coupon.minOrderAmount()) < 0) {
                throw ApiException.of(ErrorCode.COUPON_INVALID, "Order subtotal is below minimum required for coupon.");
            }

            if (coupon.maxRedemptions() != null && cartRepository.countCouponRedemptions(coupon.id()) >= coupon.maxRedemptions()) {
                throw ApiException.of(ErrorCode.COUPON_INVALID, "Coupon redemption limit reached.");
            }

            if ("PERCENT".equalsIgnoreCase(coupon.discountType())) {
                couponDiscount = subtotal.multiply(coupon.discountValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (couponDiscount.compareTo(subtotal) > 0) {
                    couponDiscount = subtotal;
                }
            } else {
                couponDiscount = coupon.discountValue().min(subtotal);
            }
        }

        BigDecimal discountedSubtotal = subtotal.subtract(couponDiscount).max(BigDecimal.ZERO);
        StorePolicy policy = storeService.policy(storeId);
        BigDecimal taxTotal = discountedSubtotal.multiply(policy.taxRate()).setScale(2, RoundingMode.HALF_UP);

        BigDecimal shippingTotal = BigDecimal.ZERO;
        LocalDate estimatedDeliveryDate = null;

        if (hasPhysical) {
            var quote = shippingRateService.quote(storeId, address.country(), totalWeight, discountedSubtotal)
                    .orElseThrow(() -> ApiException.of(ErrorCode.SHIPPING_UNAVAILABLE, "Shipping is unavailable to destination country: " + address.country()));
            shippingTotal = quote.rate();
            estimatedDeliveryDate = LocalDate.now(clock).plusDays(quote.band().estimatedDaysMax());
        }

        BigDecimal total = subtotal.add(taxTotal).add(shippingTotal).subtract(couponDiscount).max(BigDecimal.ZERO);

        // Distribute tax per item
        List<QuoteItem> quoteItems = new ArrayList<>();
        List<ComputedItem> updatedComputedItems = new ArrayList<>();
        BigDecimal accumulatedTax = BigDecimal.ZERO;

        for (int i = 0; i < computedItems.size(); i++) {
            ComputedItem ci = computedItems.get(i);
            BigDecimal itemTax;
            if (i == computedItems.size() - 1) {
                itemTax = taxTotal.subtract(accumulatedTax);
            } else {
                itemTax = subtotal.compareTo(BigDecimal.ZERO) > 0
                        ? ci.lineTotal.multiply(taxTotal).divide(subtotal, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                accumulatedTax = accumulatedTax.add(itemTax);
            }

            LocalDate itemDelivery = ci.product.physical() ? estimatedDeliveryDate : null;
            quoteItems.add(new QuoteItem(
                    ci.product.id(),
                    ci.product.title(),
                    ci.product.price(),
                    ci.quantity,
                    ci.lineTotal,
                    itemTax,
                    itemDelivery));
            updatedComputedItems.add(new ComputedItem(ci.product, ci.quantity, ci.lineTotal, itemTax, itemDelivery));
        }

        return new ComputedQuote(
                quoteItems,
                updatedComputedItems,
                subtotal,
                taxTotal,
                shippingTotal,
                couponDiscount,
                total,
                couponCodeToUse,
                couponIdToUse,
                estimatedDeliveryDate);
    }

    private Map<UUID, Integer> getPhysicalQuantities(List<OrderItemRow> items) {
        List<UUID> productIds = items.stream().map(OrderItemRow::productId).toList();
        Map<UUID, ProductInfo> products = catalogService.findByIds(productIds).stream()
                .collect(Collectors.toMap(ProductInfo::id, Function.identity()));

        Map<UUID, Integer> result = new HashMap<>();
        for (OrderItemRow item : items) {
            ProductInfo p = products.get(item.productId());
            if (p == null || p.physical()) {
                result.put(item.productId(), item.quantity());
            }
        }
        return result;
    }

    private static String hashPlaceOrderRequest(PlaceOrderRequest request) {
        String input = (request.addressId() != null ? request.addressId().toString() : "")
                + "|" + (request.couponCode() != null ? request.couponCode().trim().toLowerCase() : "")
                + "|" + (request.expectedTotal() != null ? request.expectedTotal().toPlainString() : "");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
