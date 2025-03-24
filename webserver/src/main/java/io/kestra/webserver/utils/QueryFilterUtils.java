package io.kestra.webserver.utils;

import io.kestra.core.models.QueryFilter;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
public class QueryFilterUtils {

    public static List<QueryFilter> updateFilters(List<QueryFilter> filters, ZonedDateTime resolvedStartDate) {

        return filters.stream()
            .filter(filter -> !isTimeRangeFilter(filter)) // Remove TIME_RANGE filter
            .map(filter -> isStartDateFilter(filter)
                ? createUpdatedStartDateFilter(filter, resolvedStartDate)
                : filter)
            .toList();
    }

    private static boolean isStartDateFilter(QueryFilter filter) {
        return filter.field() == QueryFilter.Field.START_DATE;
    }

    private static boolean isTimeRangeFilter(QueryFilter filter) {
        return filter.field() == QueryFilter.Field.TIME_RANGE;
    }

    private static QueryFilter createUpdatedStartDateFilter(QueryFilter filter, ZonedDateTime resolvedStartDate) {
        return QueryFilter.builder()
            .field(QueryFilter.Field.START_DATE)
            .operation(filter.operation())
            .value(resolvedStartDate.toString())
            .build();
    }
}
