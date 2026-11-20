package io.kestra.webserver.models.api;

import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.QueryValue;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * {@link Pagination} for endpoints that do not support sorting, declared the same way: {@code @Valid @RequestBean UnsortedPagination paging}.
 */
@Introspected
public record UnsortedPagination(
    @Parameter(description = "The current page") @QueryValue(value = "page", defaultValue = "1") @Min(1) int page,
    @Parameter(description = "The current page size") @QueryValue(value = "size", defaultValue = "10") @Min(1) @Max(PageableUtils.MAX_PAGE_SIZE) int size) {

    public Pageable toPageable() {
        return PageableUtils.from(page, size);
    }
}
