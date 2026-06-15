package io.kestra.webserver.utils;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ExecutionRepositoryInterface.DateFilter;
import io.kestra.core.utils.DateUtils;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryFilterUtils {

    public static void validateTimeline(List<QueryFilter> filters) {
        DateUtils.validateTimeline(filters);
    }

    private static boolean isStartDateFilter(QueryFilter filter) {
        return filter.field() == QueryFilter.Field.START_DATE;
    }

    private static boolean isEndDateFilter(QueryFilter filter) {
        return filter.field() == QueryFilter.Field.END_DATE;
    }

    private static boolean isTimeRangeFilter(QueryFilter filter) {
        return filter.field() == QueryFilter.Field.TIME_RANGE;
    }

    private static boolean isDateBoundaryFilter(QueryFilter filter) {
        return isStartDateFilter(filter) || isEndDateFilter(filter) || isTimeRangeFilter(filter);
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

    private static QueryFilter createUpdatedDateFilter(QueryFilter filter, ZonedDateTime resolvedDate, QueryFilter.Field targetField) {
        return QueryFilter.builder()
            .field(targetField)
            .operation(filter != null ? isTimeRangeFilter(filter) ? timeRangeOperation(filter) : filter.operation() : QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
            .value(resolvedDate.toString())
            .build();
    }

    private static QueryFilter createUpdatedStartDateFilter(QueryFilter filter, ZonedDateTime resolvedStartDate) {
        return createUpdatedDateFilter(filter, resolvedStartDate, QueryFilter.Field.START_DATE);
    }

    /**
     * Recursively applies {@code leafMapper} to every leaf in the filter tree, rebuilding any
     * intermediate nodes so that date-boundary leaves nested inside conditional groups are rewritten
     * just like top-level leaves.
     */
    private static QueryFilter mapLeavesRecursively(QueryFilter filter, UnaryOperator<QueryFilter> leafMapper) {
        if (filter.isNode()) {
            return QueryFilter.builder()
                .logical(filter.logical())
                .children(filter.children().stream().map(c -> mapLeavesRecursively(c, leafMapper)).toList())
                .build();
        }
        return leafMapper.apply(filter);
    }

    /**
     * Returns {@code true} if any leaf in the filter tree (including inside nested nodes) satisfies
     * {@code predicate}.
     */
    private static boolean anyLeafMatches(List<QueryFilter> filters, Predicate<QueryFilter> predicate) {
        return filters.stream().anyMatch(f -> f.isNode() ? anyLeafMatches(f.children(), predicate) : predicate.test(f));
    }

    protected static List<QueryFilter> updateFilters(List<QueryFilter> filters, ZonedDateTime resolvedStartDate) {
        boolean hasDateFilter = anyLeafMatches(filters, filter -> isStartDateFilter(filter) || isTimeRangeFilter(filter));

        List<QueryFilter> updatedFilters = new java.util.ArrayList<>(
            filters.stream()
                .map(filter -> mapLeavesRecursively(filter, leaf ->
                    isStartDateFilter(leaf) || isTimeRangeFilter(leaf)
                        ? createUpdatedStartDateFilter(leaf, resolvedStartDate)
                        : leaf
                ))
                .toList()
        );

        if (!hasDateFilter && resolvedStartDate != null) {
            updatedFilters.add(createUpdatedStartDateFilter(null, resolvedStartDate));
        }

        return updatedFilters;
    }

    /**
     * Like {@link #updateFilters} but targets {@link QueryFilter.Field#END_DATE} instead of {@code START_DATE}.
     * Used when {@link DateFilter#END_DATE} mode is active.
     */
    protected static List<QueryFilter> updateFiltersForEndDate(List<QueryFilter> filters, ZonedDateTime resolvedDate) {
        boolean hasDateFilter = anyLeafMatches(filters, QueryFilterUtils::isDateBoundaryFilter);

        List<QueryFilter> updatedFilters = new java.util.ArrayList<>(
            filters.stream()
                .map(filter -> mapLeavesRecursively(filter, leaf ->
                    isTimeRangeFilter(leaf)
                        ? createUpdatedDateFilter(leaf, resolvedDate, QueryFilter.Field.END_DATE)
                        : leaf
                ))
                .toList()
        );

        if (!hasDateFilter && resolvedDate != null) {
            updatedFilters.add(createUpdatedDateFilter(null, resolvedDate, QueryFilter.Field.END_DATE));
        }

        return updatedFilters;
    }

    public static List<QueryFilter> replaceTimeRangeWithComputedStartDateFilter(List<QueryFilter> filters) {
        return replaceTimeRangeWithComputedDateFilter(filters, DateFilter.START_DATE);
    }

    /**
     * Resolves {@link QueryFilter.Field#TIME_RANGE} into a concrete date boundary filter targeting
     * the date column(s) selected by {@code dateFilter}.
     * <ul>
     *   <li>{@link DateFilter#START_DATE} – translates TIME_RANGE to a START_DATE lower bound (existing behavior).</li>
     *   <li>{@link DateFilter#END_DATE} – translates TIME_RANGE to an END_DATE lower bound.</li>
     *   <li>{@link DateFilter#START_OR_END_DATE} – same as START_DATE; the repository handles the OR across both columns.</li>
     * </ul>
     */
    public static List<QueryFilter> replaceTimeRangeWithComputedDateFilter(List<QueryFilter> filters, DateFilter dateFilter) {
        if (dateFilter == null) {
            dateFilter = DateFilter.START_DATE;
        }
        TimeLineSearch timeLineSearch = TimeLineSearch.extractFrom(filters);
        DateUtils.validateTimeline(timeLineSearch.getStartDate(), timeLineSearch.getEndDate());
        ZonedDateTime resolvedDate = timeLineSearch.getStartDate();

        List<QueryFilter> updatedFilters = switch (dateFilter) {
            case END_DATE -> updateFiltersForEndDate(filters, resolvedDate);
            default -> updateFilters(filters, resolvedDate);
        };

        validateTimeline(updatedFilters);
        return updatedFilters;
    }

    public static final List<QueryFilter.Field> TRIGGER_DATE_FIELDS = List.of(
        QueryFilter.Field.NEXT_EXECUTION_DATE,
        QueryFilter.Field.LAST_TRIGGERED_DATE
    );

    public static List<QueryFilter> rewriteTriggerDateFilters(List<QueryFilter> filters, QueryFilter.Field dateField) {
        if (filters == null) {
            return List.of();
        }
        if (dateField != null && !TRIGGER_DATE_FIELDS.contains(dateField)) {
            throw new IllegalArgumentException(
                "dateFilter must be one of " + TRIGGER_DATE_FIELDS + " but was " + dateField
            );
        }
        QueryFilter.Field target = dateField == null ? QueryFilter.Field.NEXT_EXECUTION_DATE : dateField;
        TimeLineSearch timeLineSearch = TimeLineSearch.extractFrom(filters);
        DateUtils.validateTimeline(timeLineSearch.getStartDate(), timeLineSearch.getEndDate());
        ZonedDateTime resolvedDate = timeLineSearch.getStartDate();

        return filters.stream()
            .map(f -> mapLeavesRecursively(f, leaf -> {
                if (isTimeRangeFilter(leaf)) {
                    return QueryFilter.builder()
                        .field(target)
                        .operation(timeRangeOperation(leaf))
                        .value(resolvedDate.toString())
                        .build();
                }
                if (isStartDateFilter(leaf) || isEndDateFilter(leaf)) {
                    return QueryFilter.builder()
                        .field(target)
                        .operation(leaf.operation())
                        .value(leaf.value())
                        .build();
                }
                return leaf;
            }))
            .toList();
    }
}
