package com.bookstore.ecommerce.payment;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.Caller;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.payment.PaymentDtos.PayOrderRequest;
import com.bookstore.ecommerce.payment.PaymentDtos.Payment;
import com.bookstore.ecommerce.payment.PaymentDtos.PaymentResult;
import com.bookstore.ecommerce.payment.PaymentDtos.Refund;
import com.bookstore.ecommerce.payment.PaymentDtos.RefundRequest;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Validated
class PaymentController {

    private final PaymentServiceImpl paymentService;

    PaymentController(PaymentServiceImpl paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResult> payOrder(
            Caller caller,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PayOrderRequest request) {
        if (caller.isGuest()) {
            throw ApiException.of(ErrorCode.UNAUTHENTICATED, "Authentication required to pay for an order.");
        }
        var response = paymentService.payOrder(caller, idempotencyKey, request);
        if (response.replayed()) {
            return ResponseEntity.ok()
                    .header("Idempotent-Replayed", "true")
                    .body(response.result());
        }
        return ResponseEntity.created(URI.create("/api/v1/orders/" + request.orderId()))
                .body(response.result());
    }

    @GetMapping("/{paymentId}")
    public Payment getPayment(@PathVariable UUID paymentId, Caller caller) {
        if (caller.isGuest()) {
            throw ApiException.of(ErrorCode.UNAUTHENTICATED, "Authentication required.");
        }
        return paymentService.getPayment(paymentId, caller);
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<Refund> refundPayment(
            @PathVariable UUID paymentId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RefundRequest request,
            Caller caller) {
        if (caller.isGuest()) {
            throw ApiException.of(ErrorCode.UNAUTHENTICATED, "Authentication required.");
        }
        if (!caller.isAdmin()) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "Only administrators can issue refunds.");
        }
        var response = paymentService.refundPayment(paymentId, idempotencyKey, request, caller);
        if (response.replayed()) {
            return ResponseEntity.ok()
                    .header("Idempotent-Replayed", "true")
                    .body(response.refund());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response.refund());
    }
}
