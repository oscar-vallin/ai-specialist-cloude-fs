package com.bookstore.ecommerce.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** RFC 7807 problem details with a stable {@code code} (the {@code Problem} schema of the contract). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiProblem(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        ErrorCode code,
        String requestId,
        List<FieldIssue> errors,
        BigDecimal currentTotal,
        UUID paymentId) {

    public static final String BASE_TYPE = "https://bookstore.example/problems/";

    public static ApiProblem of(ErrorCode code, String detail, String instance, String requestId) {
        return new ApiProblem(BASE_TYPE + code.slug(), code.title(), code.status(), detail, instance, code,
                requestId, null, null, null);
    }

    public static ApiProblem of(ApiException ex, String instance, String requestId) {
        ErrorCode code = ex.code();
        return new ApiProblem(BASE_TYPE + code.slug(), code.title(), code.status(), ex.getMessage(), instance, code,
                requestId, ex.errors(), ex.currentTotal(), ex.paymentId());
    }
}
