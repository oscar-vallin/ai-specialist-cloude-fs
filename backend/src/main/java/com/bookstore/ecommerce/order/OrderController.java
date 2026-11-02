package com.bookstore.ecommerce.order;

import com.bookstore.ecommerce.common.Caller;
import com.bookstore.ecommerce.common.PageResponse;
import com.bookstore.ecommerce.order.OrderDtos.BuyAgainResult;
import com.bookstore.ecommerce.order.OrderDtos.CancelOrderRequest;
import com.bookstore.ecommerce.order.OrderDtos.CheckoutQuote;
import com.bookstore.ecommerce.order.OrderDtos.CheckoutRequest;
import com.bookstore.ecommerce.order.OrderDtos.Order;
import com.bookstore.ecommerce.order.OrderDtos.OrderSummary;
import com.bookstore.ecommerce.order.OrderDtos.PlaceOrderRequest;
import com.bookstore.ecommerce.order.OrderDtos.ReturnOrderRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@Validated
class OrderController {

    private final OrderServiceImpl orderService;

    OrderController(OrderServiceImpl orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    public CheckoutQuote checkoutQuote(Caller caller, @Valid @RequestBody CheckoutRequest request) {
        return orderService.checkoutQuote(caller, request);
    }

    @PostMapping
    public ResponseEntity<Order> placeOrder(
            Caller caller,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request) {
        var result = orderService.placeOrder(caller, idempotencyKey, request);
        if (result.replayed()) {
            return ResponseEntity.ok()
                    .header("Idempotent-Replayed", "true")
                    .body(result.order());
        }
        return ResponseEntity.created(URI.create("/api/v1/orders/" + result.order().id()))
                .body(result.order());
    }

    @GetMapping
    public PageResponse<OrderSummary> listOrders(
            Caller caller,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "placedAt,desc") @Pattern(regexp = "^placedAt(,(asc|desc))?$") String sort) {
        return orderService.listOrders(caller, status, page, size, sort);
    }

    @GetMapping("/{orderId}")
    public Order getOrder(Caller caller, @PathVariable UUID orderId) {
        return orderService.getOrder(caller, orderId);
    }

    @PostMapping("/{orderId}/cancel")
    public Order cancelOrder(
            Caller caller,
            @PathVariable UUID orderId,
            @RequestBody(required = false) CancelOrderRequest request) {
        return orderService.cancelOrder(caller, orderId, request);
    }

    @PostMapping("/{orderId}/return")
    public ResponseEntity<Order> returnOrder(
            Caller caller,
            @PathVariable UUID orderId,
            @Valid @RequestBody ReturnOrderRequest request) {
        Order order = orderService.returnOrder(caller, orderId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @PostMapping("/{orderId}/buy-again")
    public BuyAgainResult buyAgain(Caller caller, @PathVariable UUID orderId) {
        return orderService.buyAgain(caller, orderId);
    }
}
