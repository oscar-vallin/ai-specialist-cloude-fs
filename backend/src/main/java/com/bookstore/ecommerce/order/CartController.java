package com.bookstore.ecommerce.order;

import com.bookstore.ecommerce.common.Caller;
import com.bookstore.ecommerce.order.OrderDtos.AddCartItemRequest;
import com.bookstore.ecommerce.order.OrderDtos.ApplyCouponRequest;
import com.bookstore.ecommerce.order.OrderDtos.Cart;
import com.bookstore.ecommerce.order.OrderDtos.UpdateCartItemRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@Validated
class CartController {

    private final CartService cartService;

    CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public Cart getCart(
            Caller caller,
            @RequestHeader(name = "X-Guest-Token", required = false) String guestToken,
            @RequestParam(required = false) UUID storeId) {
        return cartService.getCart(caller, guestToken, storeId);
    }

    @PostMapping("/items")
    public ResponseEntity<Cart> addCartItem(
            Caller caller,
            @RequestHeader(name = "X-Guest-Token", required = false) String guestToken,
            @RequestParam(required = false) UUID storeId,
            @Valid @RequestBody AddCartItemRequest request) {
        var result = cartService.addCartItem(caller, guestToken, storeId, request);
        var response = ResponseEntity.status(HttpStatus.CREATED);
        if (result.newGuestToken() != null) {
            response.header("X-Guest-Token", result.newGuestToken());
        }
        return response.body(result.cart());
    }

    @PutMapping("/items/{itemId}")
    public Cart updateCartItem(
            Caller caller,
            @RequestHeader(name = "X-Guest-Token", required = false) String guestToken,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateCartItem(caller, guestToken, itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCartItem(
            Caller caller,
            @RequestHeader(name = "X-Guest-Token", required = false) String guestToken,
            @PathVariable UUID itemId) {
        cartService.removeCartItem(caller, guestToken, itemId);
    }

    @PutMapping("/coupon")
    public Cart applyCoupon(
            Caller caller,
            @RequestHeader(name = "X-Guest-Token", required = false) String guestToken,
            @Valid @RequestBody ApplyCouponRequest request) {
        return cartService.applyCoupon(caller, guestToken, request);
    }

    @DeleteMapping("/coupon")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCoupon(
            Caller caller,
            @RequestHeader(name = "X-Guest-Token", required = false) String guestToken) {
        cartService.removeCoupon(caller, guestToken);
    }
}
