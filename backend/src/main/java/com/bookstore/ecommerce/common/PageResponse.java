package com.bookstore.ecommerce.common;

import java.util.List;

/** Paged list response: {@code content} plus the counts required by docs/api-conventions.md. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
        int pages = size == 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageResponse<>(content, page, size, total, pages);
    }
}
