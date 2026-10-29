package com.bookstore.ecommerce.common;

/** One field-level validation error. The rejected value is deliberately not included (it may be sensitive). */
public record FieldIssue(String field, String message) {
}
