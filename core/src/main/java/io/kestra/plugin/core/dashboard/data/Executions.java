package io.kestra.plugin.core.dashboard.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.dashboards.filters.Contains;
import io.kestra.core.models.dashboards.filters.GreaterThanOrEqualTo;
import io.kestra.core.models.dashboards.filters.LessThanOrEqualTo;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.QueryBuilderInterface;
import io.kestra.core.validations.ExecutionsDataFilterValidation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@EqualsAndHashCode
@ExecutionsDataFilterValidation
@Schema(
    title = "Display Execution data in a dashboard chart.",
    description = "Execution data can be displayed in charts broken out by Namespace and filtered by State, for example."
)
@Plugin(
    examples = {
        @Example(
            title = "Display a chart with a Executions per Namespace broken out by State.",
            full = true,
            code = {
                "id: executions_per_namespace_bars\n" +
                "type: io.kestra.plugin.core.dashboard.chart.Bar\n" +
                "chartOptions:\n" +
                  "displayName: Executions (per namespace)\n" +
                  "description: Executions count per namespace\n" +
                  "legend:\n" +
                    "enabled: true\n" +
                  "column: namespace\n" +
                "data\n" +
                  "type: io.kestra.plugin.core.dashboard.data.Executions\n" +
                  "columns:\n" +
                    "namespace:\n" +
                      "field: NAMESPACE\n" +
                    "state:\n" +
                      "field: STATE\n" +
                    "total:\n" +
                      "displayName: Executions\n" +
                      "agg: COUNT\n"
            }
        )
    }
)
@JsonTypeName("Executions")
public class Executions<C extends ColumnDescriptor<Executions.Fields>> extends DataFilter<Executions.Fields, C> {
    @Override
    public Class<? extends QueryBuilderInterface<Executions.Fields>> repositoryClass() {
        return ExecutionRepositoryInterface.class;
    }

    @Override
    public void setGlobalFilter(List<QueryFilter> filters, ZonedDateTime startDate, ZonedDateTime endDate) {
        List<AbstractFilter<Fields>> where = this.getWhere() != null ? new ArrayList<>(this.getWhere()) : new ArrayList<>();

        if (filters == null) {
            return;
        }

        List<QueryFilter> namespaceFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.NAMESPACE)).toList();
        if (!namespaceFilters.isEmpty()) {
            where.removeIf(filter -> filter.getField().equals(Executions.Fields.NAMESPACE));
            namespaceFilters.forEach(f -> {
                where.add(f.toDashboardFilterBuilder(Executions.Fields.NAMESPACE, f.value()));
            });
        }

        List<QueryFilter> labelFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.LABELS)).toList();
        if (!labelFilters.isEmpty()) {
            where.removeIf(filter -> filter.getField().equals(Fields.LABELS));
            labelFilters.forEach(f -> {
                where.add(Contains.<Executions.Fields>builder().field(Fields.LABELS).value(f.value()).build());
            });
        }


        if (startDate != null || endDate != null) {
            if (startDate != null) {
                where.removeIf(f -> f.getField().equals(Fields.START_DATE));
                where.add(GreaterThanOrEqualTo.<Executions.Fields>builder().field(Fields.START_DATE).value(startDate.toInstant()).build());
            }
            if (endDate != null) {
                where.removeIf(f -> f.getField().equals(Fields.END_DATE));
                where.add(LessThanOrEqualTo.<Executions.Fields>builder().field(Fields.END_DATE).value(endDate.toInstant()).build());
            }
        }

        this.setWhere(where);
    }

    public enum Fields {
        ID,
        NAMESPACE,
        FLOW_ID,
        FLOW_REVISION,
        STATE,
        DURATION,
        LABELS,
        START_DATE,
        END_DATE,
        TRIGGER_EXECUTION_ID
    }
}
