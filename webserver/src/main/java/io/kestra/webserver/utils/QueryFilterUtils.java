package io.kestra.webserver.utils;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.utils.DateUtils;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
public class QueryFilterUtils {

    public static void validateTimeline(List<QueryFilter> filters) {
        DateUtils.validateTimeline(filters);
    }

    private static boolean isStartDateFilter(QueryFilter filter) {
        return filter.field() == QueryFilter.Field.START_DATE;
    }

    private static boolean isTimeRangeFilter(QueryFilter filter) {
        return filter.field() == QueryFilter.Field.TIME_RANGE;
    }

    /**
     * If a time range is provided, then if it's a negative filter, we use the filter LESS_THAN_OR_EQUAL_TO.
     *
     * @param filter The query filter.
     * @return The updated query filter operation.
     */
    private static QueryFilter.Op timeRangeOperation(QueryFilter filter) {
        return switch (filter.operation()) {
            case NOT_EQUALS, NOT_IN -> QueryFilter.Op.LESS_THAN_OR_EQUAL_TO;
            default -> QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO;
        };
    }

    private static QueryFilter createUpdatedStartDateFilter(QueryFilter filter, ZonedDateTime resolvedStartDate) {
        return QueryFilter.builder()
            .field(QueryFilter.Field.START_DATE)
            .operation(filter != null ?
                isTimeRangeFilter(filter)  ? timeRangeOperation(filter): filter.operation() :
                QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
            .value(resolvedStartDate.toString())
            .build();
    }
    protected static List<QueryFilter> updateFilters(List<QueryFilter> filters, ZonedDateTime resolvedStartDate) {
        boolean hasDateFilter = filters.stream().anyMatch(filter -> isStartDateFilter(filter) || isTimeRangeFilter(filter));

        List<QueryFilter> updatedFilters = new java.util.ArrayList<>(filters.stream()
            .map(filter -> isStartDateFilter(filter) || isTimeRangeFilter(filter)
                ? createUpdatedStartDateFilter(filter, resolvedStartDate)
                : filter)
            .toList());

        if (!hasDateFilter && resolvedStartDate != null) {
            updatedFilters.add(createUpdatedStartDateFilter(null, resolvedStartDate));
        }

        return updatedFilters;
    }

    public static List<QueryFilter> replaceTimeRangeWithComputedStartDateFilter(List<QueryFilter> filters) {
        TimeLineSearch timeLineSearch = TimeLineSearch.extractFrom(filters);
        DateUtils.validateTimeline(timeLineSearch.getStartDate(), timeLineSearch.getEndDate());
        ZonedDateTime resolvedStartDate = timeLineSearch.getStartDate();
        var updateFilters = updateFilters(filters, resolvedStartDate);
        validateTimeline(updateFilters);
        return updateFilters;
    }
}
