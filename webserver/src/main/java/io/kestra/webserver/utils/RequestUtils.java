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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RequestUtils {
    public static Map<String, String> toMap(List<String> queryString) {
        return queryString == null ? null : queryString
            .stream()
            .map(s -> {
                String[] split = s.split("[: ]+");
                if (split.length < 2 || split[0] == null || split[0].isEmpty()) {
                    throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid queryString parameter");
                }

                return new AbstractMap.SimpleEntry<>(
                    split[0],
                    s.substring(s.indexOf(":") + 1).trim()
                );
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static List<QueryFilter> mapLegacyParamsToFilters(
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
                .operation(QueryFilter.Op.STARTS_WITH_NAMESPACE_PREFIX)
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

    public static List<FlowScope> toFlowScopes(List<String> values) {
        return Arrays.stream(values.getFirst().split(","))
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
