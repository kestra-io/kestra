package io.kestra.plugin.core.dashboard.data;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.event.Level;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.dashboards.filters.GreaterThanOrEqualTo;
import io.kestra.core.models.dashboards.filters.In;
import io.kestra.core.models.dashboards.filters.LessThanOrEqualTo;
import io.kestra.core.models.executions.LogEntry;

public interface ILogs extends IData<ILogs.Fields> {

    default List<AbstractFilter<ILogs.Fields>> whereWithGlobalFilters(List<QueryFilter> filters, ZonedDateTime startDate, ZonedDateTime endDate, List<AbstractFilter<ILogs.Fields>> where) {
        List<AbstractFilter<ILogs.Fields>> updatedWhere = where != null ? new ArrayList<>(where) : new ArrayList<>();

        if (filters != null) {

            List<QueryFilter> namespaceFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.NAMESPACE)).toList();
            if (!namespaceFilters.isEmpty()) {
                updatedWhere.removeIf(filter -> filter.getField().equals(Fields.NAMESPACE));
                namespaceFilters.forEach(f ->
                {
                    updatedWhere.add(f.toDashboardFilterBuilder(Fields.NAMESPACE, f.value()));
                });
            }

            List<QueryFilter> flowFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.FLOW_ID)).toList();
            if (!flowFilters.isEmpty()) {
                updatedWhere.removeIf(filter -> filter.getField().equals(Fields.FLOW_ID));
                flowFilters.forEach(f ->
                {
                    updatedWhere.add(f.toDashboardFilterBuilder(Fields.FLOW_ID, f.value()));
                });
            }

            List<QueryFilter> levelFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.LEVEL)).toList();
            if (!levelFilters.isEmpty()) {
                updatedWhere.removeIf(filter -> filter.getField().equals(Fields.LEVEL));
                levelFilters.forEach(f ->
                {
                    Level level = f.value() instanceof Level l ? l : Level.valueOf((String) f.value());
                    List<Level> levels = switch (f.operation()) {
                        case GREATER_THAN_OR_EQUAL_TO -> LogEntry.findLevelsByMin(level);
                        case LESS_THAN_OR_EQUAL_TO -> LogEntry.findLevelsByMax(level);
                        default -> throw new IllegalArgumentException("Unsupported operation for LEVEL: " + f.operation());
                    };
                    updatedWhere.add(In.<Fields>builder()
                        .field(Fields.LEVEL)
                        .values(levels.stream().map(Enum::name).map(Object.class::cast).toList())
                        .build());
                });
            }
        }

        if (startDate != null || endDate != null) {
            updatedWhere.removeIf(f -> f.getField().equals(Fields.DATE));
            if (startDate != null) {
                updatedWhere.add(GreaterThanOrEqualTo.<Fields> builder().field(Fields.DATE).value(startDate.toInstant()).build());
            }
            if (endDate != null) {
                updatedWhere.add(LessThanOrEqualTo.<Fields> builder().field(Fields.DATE).value(endDate.toInstant()).build());
            }
        }

        return updatedWhere;
    }

    enum Fields {
        NAMESPACE,
        FLOW_ID,
        EXECUTION_ID,
        TASK_ID,
        DATE,
        TASK_RUN_ID,
        ATTEMPT_NUMBER,
        TRIGGER_ID,
        LEVEL,
        MESSAGE
    }
}
