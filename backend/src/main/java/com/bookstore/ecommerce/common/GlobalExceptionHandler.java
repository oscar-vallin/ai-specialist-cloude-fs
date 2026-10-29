package com.bookstore.ecommerce.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every failure to an RFC 7807 problem. Messages never echo request content: bodies can carry card data,
 * so parse errors use a fixed text and field errors never include the rejected value.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiProblem> apiException(ApiException ex, HttpServletRequest request) {
        return respond(ApiProblem.of(ex, request.getRequestURI(), RequestIdFilter.current()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiProblem> invalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldIssue> issues = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldIssue(e.getField(), e.getDefaultMessage()))
                .toList();
        List<FieldIssue> global = ex.getBindingResult().getGlobalErrors().stream()
                .map(e -> new FieldIssue(e.getObjectName(), e.getDefaultMessage()))
                .toList();
        return validation(request, concat(issues, global));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiProblem> invalidParameters(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<FieldIssue> issues = ex.getAllErrors().stream()
                .map(e -> new FieldIssue("request", e.getDefaultMessage()))
                .toList();
        return validation(request, issues);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiProblem> constraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldIssue> issues = ex.getConstraintViolations().stream()
                .map(v -> new FieldIssue(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return validation(request, issues);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiProblem> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return validation(request, List.of(new FieldIssue(ex.getName(), "has an invalid format")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiProblem> missingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return validation(request, List.of(new FieldIssue(ex.getParameterName(), "is required")));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiProblem> missingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        if ("Idempotency-Key".equalsIgnoreCase(ex.getHeaderName())) {
            return respond(ApiProblem.of(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "The Idempotency-Key header is required.", request.getRequestURI(), RequestIdFilter.current()));
        }
        return validation(request, List.of(new FieldIssue(ex.getHeaderName(), "is required")));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ApiProblem> unreadable(Exception ex, HttpServletRequest request) {
        // Fixed text on purpose: the parser message can quote the offending value.
        return respond(ApiProblem.of(ErrorCode.MALFORMED_REQUEST,
                "The request body is missing, malformed or has an unsupported content type.",
                request.getRequestURI(), RequestIdFilter.current()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiProblem> methodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(ApiProblem.of(ErrorCode.METHOD_NOT_ALLOWED, "Method not allowed for this resource.",
                request.getRequestURI(), RequestIdFilter.current()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiProblem> noResource(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(ApiProblem.of(ErrorCode.RESOURCE_NOT_FOUND, "No such resource.", request.getRequestURI(),
                RequestIdFilter.current()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiProblem> optimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return respond(ApiProblem.of(ErrorCode.CONCURRENT_MODIFICATION,
                "The resource was modified by another request. Retry.", request.getRequestURI(),
                RequestIdFilter.current()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiProblem> integrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return respond(ApiProblem.of(ErrorCode.CONCURRENT_MODIFICATION,
                "The request conflicts with concurrent changes. Retry.", request.getRequestURI(),
                RequestIdFilter.current()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiProblem> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ApiProblem.of(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.",
                request.getRequestURI(), RequestIdFilter.current()));
    }

    private static ResponseEntity<ApiProblem> validation(HttpServletRequest request, List<FieldIssue> issues) {
        ApiProblem base = ApiProblem.of(ErrorCode.VALIDATION_FAILED, "The request is invalid.",
                request.getRequestURI(), RequestIdFilter.current());
        return respond(new ApiProblem(base.type(), base.title(), base.status(), base.detail(), base.instance(),
                base.code(), base.requestId(), issues, null, null));
    }

    private static List<FieldIssue> concat(List<FieldIssue> a, List<FieldIssue> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }

    static ResponseEntity<ApiProblem> respond(ApiProblem problem) {
        return ResponseEntity.status(problem.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
