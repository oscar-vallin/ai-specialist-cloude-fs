package com.bookstore.ecommerce.order;

import com.bookstore.ecommerce.catalog.CatalogService;
import com.bookstore.ecommerce.catalog.CatalogService.ProductInfo;
import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.Caller;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.config.AppProperties;
import com.bookstore.ecommerce.member.GuestCartMerger;
import com.bookstore.ecommerce.order.CartRepository.CartItemRow;
import com.bookstore.ecommerce.order.CartRepository.CartRow;
import com.bookstore.ecommerce.order.CartRepository.CouponRow;
import com.bookstore.ecommerce.order.OrderDtos.AddCartItemRequest;
import com.bookstore.ecommerce.order.OrderDtos.ApplyCouponRequest;
import com.bookstore.ecommerce.order.OrderDtos.Cart;
import com.bookstore.ecommerce.order.OrderDtos.CartItem;
import com.bookstore.ecommerce.order.OrderDtos.UpdateCartItemRequest;
import com.bookstore.ecommerce.store.StoreService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

public interface CartService extends GuestCartMerger {

    Cart getCart(Caller caller, String guestToken, UUID storeId);

    CartWithHeader addCartItem(Caller caller, String guestToken, UUID storeId, AddCartItemRequest request);

    Cart updateCartItem(Caller caller, String guestToken, UUID itemId, UpdateCartItemRequest request);

    void removeCartItem(Caller caller, String guestToken, UUID itemId);

    Cart applyCoupon(Caller caller, String guestToken, ApplyCouponRequest request);

    void removeCoupon(Caller caller, String guestToken);

    record CartWithHeader(Cart cart, String newGuestToken) {
    }
}

@Service
class CartServiceImpl implements CartService {

    private final CartRepository repository;
    private final CatalogService catalog;
    private final StoreService stores;
    private final AppProperties properties;
    private final Clock clock;

    CartServiceImpl(
            CartRepository repository,
            CatalogService catalog,
            StoreService stores,
            AppProperties properties,
            Clock clock) {
        this.repository = repository;
        this.catalog = catalog;
        this.stores = stores;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Cart getCart(Caller caller, String guestToken, UUID storeId) {
        UUID store = stores.resolveStoreId(storeId);
        CartRow cartRow = resolveCart(caller, guestToken, store, false);
        return buildCartDto(cartRow, caller);
    }

    @Override
    @Transactional
    public CartWithHeader addCartItem(Caller caller, String guestToken, UUID storeId, AddCartItemRequest request) {
        UUID store = stores.resolveStoreId(storeId);
        String newGuestToken = null;
        CartRow cartRow;

        if (caller.isGuest()) {
            if (guestToken == null || guestToken.isBlank()) {
                newGuestToken = UUID.randomUUID().toString().replace("-", "");
                UUID cartId = repository.createCart(store, null, newGuestToken);
                cartRow = repository.findById(cartId).orElseThrow();
            } else {
                Optional<CartRow> existing = repository.findActiveByGuestToken(guestToken);
                if (existing.isEmpty()) {
                    newGuestToken = UUID.randomUUID().toString().replace("-", "");
                    UUID cartId = repository.createCart(store, null, newGuestToken);
                    cartRow = repository.findById(cartId).orElseThrow();
                } else {
                    cartRow = existing.get();
                }
            }
        } else {
            cartRow = resolveCart(caller, null, store, true);
        }

        ProductInfo product = catalog.findVisible(request.productId(), cartRow.storeId(), caller)
                .orElseThrow(() -> ApiException.of(ErrorCode.PRODUCT_NOT_FOUND, "Product not found: " + request.productId()));

        Optional<CartItemRow> existingItem = repository.findItemByProduct(cartRow.id(), request.productId());
        int currentQty = existingItem.map(CartItemRow::quantity).orElse(0);
        int totalQty = currentQty + request.quantity();

        if (product.physical() && totalQty > product.available()) {
            throw ApiException.of(ErrorCode.INSUFFICIENT_STOCK, "Insufficient stock for " + product.title());
        }

        if (existingItem.isPresent()) {
            repository.updateItemQuantity(existingItem.get().id(), totalQty);
        } else {
            repository.insertItem(cartRow.id(), request.productId(), request.quantity());
        }

        Cart cartDto = buildCartDto(cartRow, caller);
        return new CartWithHeader(cartDto, newGuestToken);
    }

    @Override
    @Transactional
    public Cart updateCartItem(Caller caller, String guestToken, UUID itemId, UpdateCartItemRequest request) {
        CartItemRow item = repository.findItemById(itemId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CART_ITEM_NOT_FOUND, "Cart item not found: " + itemId));

        CartRow cartRow = repository.findById(item.cartId())
                .orElseThrow(() -> ApiException.of(ErrorCode.CART_NOT_FOUND, "Cart not found"));

        validateCartOwnership(cartRow, caller, guestToken);

        ProductInfo product = catalog.findVisible(item.productId(), cartRow.storeId(), caller)
                .orElseThrow(() -> ApiException.of(ErrorCode.PRODUCT_NOT_FOUND, "Product not found: " + item.productId()));

        if (product.physical() && request.quantity() > product.available()) {
            throw ApiException.of(ErrorCode.INSUFFICIENT_STOCK, "Insufficient stock for " + product.title());
        }

        repository.updateItemQuantity(itemId, request.quantity());
        return buildCartDto(cartRow, caller);
    }

    @Override
    @Transactional
    public void removeCartItem(Caller caller, String guestToken, UUID itemId) {
        CartItemRow item = repository.findItemById(itemId)
                .orElseThrow(() -> ApiException.of(ErrorCode.CART_ITEM_NOT_FOUND, "Cart item not found: " + itemId));

        CartRow cartRow = repository.findById(item.cartId())
                .orElseThrow(() -> ApiException.of(ErrorCode.CART_NOT_FOUND, "Cart not found"));

        validateCartOwnership(cartRow, caller, guestToken);
        repository.deleteItem(itemId);
    }

    @Override
    @Transactional
    public Cart applyCoupon(Caller caller, String guestToken, ApplyCouponRequest request) {
        CartRow cartRow = resolveCartForModification(caller, guestToken);

        CouponRow coupon = repository.findCouponByCode(request.code(), cartRow.storeId())
                .orElseThrow(() -> ApiException.of(ErrorCode.COUPON_INVALID, "Coupon is invalid."));

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!coupon.active()
                || (coupon.validFrom() != null && now.isBefore(coupon.validFrom()))
                || (coupon.validUntil() != null && now.isAfter(coupon.validUntil()))) {
            throw ApiException.of(ErrorCode.COUPON_INVALID, "Coupon is expired or inactive.");
        }

        repository.setCoupon(cartRow.id(), coupon.id());
        return buildCartDto(cartRow, caller);
    }

    @Override
    @Transactional
    public void removeCoupon(Caller caller, String guestToken) {
        CartRow cartRow = resolveCartForModification(caller, guestToken);
        repository.removeCoupon(cartRow.id());
    }

    @Override
    @Transactional
    public boolean merge(UUID userId, String guestToken) {
        if (guestToken == null || guestToken.isBlank()) {
            return false;
        }
        Optional<CartRow> guestCartOpt = repository.findActiveByGuestToken(guestToken);
        if (guestCartOpt.isEmpty()) {
            return false;
        }

        CartRow guestCart = guestCartOpt.get();
        CartRow userCart = repository.findActiveByUser(userId, guestCart.storeId())
                .orElseGet(() -> {
                    UUID id = repository.createCart(guestCart.storeId(), userId, null);
                    return repository.findById(id).orElseThrow();
                });

        List<CartItemRow> guestItems = repository.findItems(guestCart.id());
        for (CartItemRow gItem : guestItems) {
            Optional<CartItemRow> uItem = repository.findItemByProduct(userCart.id(), gItem.productId());
            if (uItem.isPresent()) {
                repository.updateItemQuantity(uItem.get().id(), uItem.get().quantity() + gItem.quantity());
            } else {
                repository.insertItem(userCart.id(), gItem.productId(), gItem.quantity());
            }
        }

        if (userCart.couponId() == null && guestCart.couponId() != null) {
            repository.setCoupon(userCart.id(), guestCart.couponId());
        }

        repository.markMerged(guestCart.id(), userCart.id());
        return true;
    }

    private CartRow resolveCart(Caller caller, String guestToken, UUID storeId, boolean createIfMissing) {
        if (!caller.isGuest()) {
            return repository.findActiveByUser(caller.userId(), storeId)
                    .orElseGet(() -> {
                        if (createIfMissing) {
                            UUID id = repository.createCart(storeId, caller.userId(), null);
                            return repository.findById(id).orElseThrow();
                        }
                        UUID id = repository.createCart(storeId, caller.userId(), null);
                        return repository.findById(id).orElseThrow();
                    });
        }

        if (guestToken == null || guestToken.isBlank()) {
            throw ApiException.of(ErrorCode.CART_NOT_FOUND, "Guest cart token is missing.");
        }
        return repository.findActiveByGuestToken(guestToken)
                .orElseThrow(() -> ApiException.of(ErrorCode.CART_NOT_FOUND, "Guest cart not found."));
    }

    private CartRow resolveCartForModification(Caller caller, String guestToken) {
        if (!caller.isGuest()) {
            UUID defaultStore = stores.resolveStoreId(null);
            return repository.findActiveByUser(caller.userId(), defaultStore)
                    .orElseGet(() -> {
                        UUID id = repository.createCart(defaultStore, caller.userId(), null);
                        return repository.findById(id).orElseThrow();
                    });
        }
        if (guestToken == null || guestToken.isBlank()) {
            throw ApiException.of(ErrorCode.CART_NOT_FOUND, "Guest token required.");
        }
        return repository.findActiveByGuestToken(guestToken)
                .orElseThrow(() -> ApiException.of(ErrorCode.CART_NOT_FOUND, "Cart not found."));
    }

    private void validateCartOwnership(CartRow cart, Caller caller, String guestToken) {
        if (!caller.isGuest()) {
            if (!caller.userId().equals(cart.userId())) {
                throw ApiException.of(ErrorCode.CART_ITEM_NOT_FOUND, "Cart item not found.");
            }
        } else {
            if (guestToken == null || !guestToken.equals(cart.guestToken())) {
                throw ApiException.of(ErrorCode.CART_ITEM_NOT_FOUND, "Cart item not found.");
            }
        }
    }

    private Cart buildCartDto(CartRow cartRow, Caller caller) {
        List<CartItemRow> itemRows = repository.findItems(cartRow.id());
        List<UUID> productIds = itemRows.stream().map(CartItemRow::productId).toList();
        Map<UUID, ProductInfo> products = catalog.findByIds(productIds).stream()
                .collect(Collectors.toMap(ProductInfo::id, Function.identity()));

        List<CartItem> items = new ArrayList<>();
        int itemCount = 0;
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItemRow itemRow : itemRows) {
            ProductInfo p = products.get(itemRow.productId());
            BigDecimal unitPrice = p != null ? p.price() : BigDecimal.ZERO;
            String title = p != null ? p.title() : "";
            String cover = p != null ? p.coverFrontUrl() : "";
            boolean inStock = p != null && (!p.physical() || p.available() >= itemRow.quantity());
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(itemRow.quantity()));

            items.add(new CartItem(
                    itemRow.id(),
                    itemRow.productId(),
                    title,
                    cover,
                    unitPrice,
                    itemRow.quantity(),
                    lineTotal,
                    inStock));

            itemCount += itemRow.quantity();
            subtotal = subtotal.add(lineTotal);
        }

        String couponCode = null;
        if (cartRow.couponId() != null) {
            couponCode = repository.findCouponById(cartRow.couponId()).map(CouponRow::code).orElse(null);
        }

        return new Cart(
                cartRow.id(),
                cartRow.storeId(),
                "ACTIVE",
                cartRow.guestToken(),
                items,
                itemCount,
                subtotal,
                couponCode,
                properties.currency());
    }
}
