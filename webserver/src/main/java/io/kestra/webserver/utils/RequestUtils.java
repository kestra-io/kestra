package io.kestra.webserver.utils;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.flows.FlowScope;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.slf4j.event.Level;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RequestUtils {
    private static final String QUERY_STRING_SEPARATOR = ":";

    /**
     * Transform colon-separated items to a {@link Map}.
     *
     * @param queryString the list of {@code key:value} parameters
     * @return the map of split pairs
     * @throws HttpStatusException when items can't be reliably split by the separator or there are duplicate keys
     */
    public static Map<String, String> toMap(List<String> queryString) {
        if (queryString == null) return Map.of();

        Stream<AbstractMap.SimpleEntry<String, String>> entryStream = queryString
            .stream()
            .map(s -> {
                String[] split = s.split(QUERY_STRING_SEPARATOR, 2);
                if (split.length < 2 || split[0] == null || split[0].isEmpty()) {
                    throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Can't split the queryString parameter by ':'");
                }

                final String key = split[0].trim();
                final String value = split[1].trim();

                if (key.isEmpty() || value.isEmpty()) {
                    throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Can't have an empty part of queryString");
                }

                if (key.matches(".*\\s.*")) {
                    throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Key of queryString can't contain a whitespace character");
                }

                return new AbstractMap.SimpleEntry<>(key, value);
            });

        try {
            return entryStream.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (IllegalStateException e) {
            throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The queryString parameter keys contains duplicities: " + e.getMessage());
        }
    }

    /**
     * if filters is defined, use that, otherwise map all legacy params to the new filter API
     * if you are manipulating an entity queryable by date, use {@link RequestUtils#getFiltersOrDefaultToLegacyMapping(List, String, String, String, String, Level, ZonedDateTime, ZonedDateTime, List, List, Duration, ExecutionRepositoryInterface.ChildFilter, List, String, String)} instead
     *
     * @return the new filter list
     */
    public static List<QueryFilter> getFiltersOrDefaultToLegacyMapping(
        List<QueryFilter> filters,
        String query,
        String namespace,
        String flowId,
        String triggerId,
        Level minLevel,
        List<FlowScope> scope,
        List<String> labels,
        ExecutionRepositoryInterface.ChildFilter childFilter,
        List<State.Type> state,
        String workerId,
        String triggerExecutionId
    ) {
        if (filters != null && !filters.isEmpty()) {
            return filters;
        }
        return mapLegacyParamsToFilters(
            query,
            namespace,
            flowId,
            triggerId,
            minLevel,
            null,
            null,
            scope,
            labels,
            null,
            childFilter,
            state,
            workerId,
            triggerExecutionId
        );
    }

    /**
     * same as {@link RequestUtils#getFiltersOrDefaultToLegacyMapping(List, String, String, String, String, Level, List, List, ExecutionRepositoryInterface.ChildFilter, List, String, String)}
     * , it additionally adds an Entity queryable by date, do date validation, and potentially add startDate filter
     *
     * @return the new filter list with dates handled, and a potential default startDate filter
     */
    public static List<QueryFilter> getFiltersOrDefaultToLegacyMapping(
        List<QueryFilter> filters,
        String query,
        String namespace,
        String flowId,
        String triggerId,
        Level minLevel,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        List<FlowScope> scope,
        List<String> labels,
        Duration timeRange,
        ExecutionRepositoryInterface.ChildFilter childFilter,
        List<State.Type> state,
        String workerId,
        String triggerExecutionId
    ) {
        if (filters != null && !filters.isEmpty()) {
            return QueryFilterUtils.replaceTimeRangeWithComputedStartDateFilter(filters);
        }
        return QueryFilterUtils.replaceTimeRangeWithComputedStartDateFilter(
            mapLegacyParamsToFilters(
                query,
                namespace,
                flowId,
                triggerId,
                minLevel,
                startDate,
                endDate,
                scope,
                labels,
                timeRange,
                childFilter,
                state,
                workerId,
                triggerExecutionId
            )
        );
    }

    private static List<QueryFilter> mapLegacyParamsToFilters(
        String query,
        String namespace,
        String flowId,
        String triggerId,
        Level minLevel,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        List<FlowScope> scope,
        List<String> labels,
        Duration timeRange,
        ExecutionRepositoryInterface.ChildFilter childFilter,
        List<State.Type> state,
        String workerId,
        String triggerExecutionId
    ) {
        List<QueryFilter> filters = new ArrayList<>();

        if (query != null) {
            filters.add(QueryFilter.builder()
                .field(QueryFilter.Field.QUERY)
                .operation(QueryFilter.Op.EQUALS)
                .value(query)
                .build());
        }

        if (namespace != null) {
            filters.add(QueryFilter.builder()
                .field(QueryFilter.Field.NAMESPACE)
                .operation(QueryFilter.Op.PREFIX)
                .value(namespace)
                .build());
        }

        if (flowId != null) {
            filters.add(QueryFilter.builder()
                .field(QueryFilter.Field.FLOW_ID)
                .operation(QueryFilter.Op.EQUALS)
                .value(flowId)
                .build());
        }

        if (triggerId != null) {
            filters.add(QueryFilter.builder()
                .field(QueryFilter.Field.TRIGGER_ID)
                .operation(QueryFilter.Op.EQUALS)
                .value(triggerId)
                .build());
        }

        if (minLevel != null) {
            filters.add(QueryFilter.builder()
                .field(QueryFilter.Field.MIN_LEVEL)
                .operation(QueryFilter.Op.EQUALS)
                .value(minLevel.name())
                .build());
        }

        if (startDate != null) {
            filters.add(QueryFilter.builder()
                .field(QueryFilter.Field.START_DATE)
                .operation(QueryFilter.Op.GREATER_THAN)
                .value(startDate.toString())
                .build());
        }

        if (endDate != null) {
            filters.add(QueryFilter.builder()
                .field(QueryFilter.Field.END_DATE)
                .operation(QueryFilter.Op.LESS_THAN)
                .value(endDate.toString())
                .build());
        }
        if (scope != null) {
            filters.add(QueryFilter.builder()
                .field(QueryFilter.Field.SCOPE)
                .operation(QueryFilter.Op.EQUALS)
                .value(scope)
                .build());
        }
        if (labels != null && !labels.isEmpty()) {
            filters.add(QueryFilter.builder()
                .field(QueryFilter.Field.LABELS)
                .operation(QueryFilter.Op.EQUALS)
                .value(RequestUtils.toMap(labels))
                .build());
        }
        if (timeRange != null) {
            filters.add(QueryFilter.builder()
                .field(QueryFilter.Field.TIME_RANGE)
                .operation(QueryFilter.Op.EQUALS)
                .value(timeRange)
                .build());
        }
        if (childFilter != null) {
            filters.add(QueryFilter.builder()
                .field(QueryFilter.Field.CHILD_FILTER)
                .operation(QueryFilter.Op.EQUALS)
                .value(childFilter)
                .build());
        }
        if (state != null) {
            filters.add(QueryFilter.builder()
                .field(QueryFilter.Field.STATE)
                .operation(QueryFilter.Op.IN)
                .value(state)
                .build());
        }
        if (workerId != null) {
            filters.add(QueryFilter.builder()
                .field(QueryFilter.Field.WORKER_ID)
                .operation(QueryFilter.Op.EQUALS)
                .value(workerId)
                .build());
        }
        if (triggerExecutionId != null) {
            filters.add(QueryFilter.builder()
                .field(Field.TRIGGER_EXECUTION_ID)
                .operation(QueryFilter.Op.EQUALS)
                .value(triggerExecutionId)
                .build());
        }

        return filters;
    }

    public static List<FlowScope> toFlowScopes(String value) {
        return Arrays.stream(value.split(","))
            .map(valueStr -> {
                try {
                    return FlowScope.valueOf(valueStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid FlowScope value: " + valueStr, e);
                }
            })
            .collect(Collectors.toList());
    }

}
