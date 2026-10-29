package com.bookstore.ecommerce.common;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A business or validation failure that is reported to the client as an RFC 7807 problem. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private List<FieldIssue> errors;
    private BigDecimal currentTotal;
    private UUID paymentId;

    public ApiException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public static ApiException of(ErrorCode code, String detail) {
        return new ApiException(code, detail);
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "The request is invalid.")
                .withErrors(List.of(new FieldIssue(field, message)));
    }

    public ApiException withErrors(List<FieldIssue> errors) {
        this.errors = errors;
        return this;
    }

    public ApiException withCurrentTotal(BigDecimal currentTotal) {
        this.currentTotal = currentTotal;
        return this;
    }

    public ApiException withPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
        return this;
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldIssue> errors() {
        return errors;
    }

    public BigDecimal currentTotal() {
        return currentTotal;
    }

    public UUID paymentId() {
        return paymentId;
    }
}
