package io.kestra.webserver.models.api;

import java.util.List;
import java.util.function.Function;

import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.QueryValue;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Declare it as {@code @Valid @RequestBean Pagination paging}: without {@code @Valid} the bounds are not enforced.
 */
@Introspected
public record Pagination(
    @Parameter(description = "The current page") @QueryValue(value = "page", defaultValue = "1") @Min(1) int page,
    @Parameter(description = "The current page size") @QueryValue(value = "size", defaultValue = "10") @Min(1) @Max(PageableUtils.MAX_PAGE_SIZE) int size,
    @Parameter(description = "The sort of current page") @Nullable @QueryValue(value = "sort") List<String> sort) {

    public Pageable toPageable() {
        return PageableUtils.from(page, size, sort);
    }

    public Pageable toPageable(Function<String, String> sortMapper) {
        return PageableUtils.from(page, size, sort, sortMapper);
    }
}
