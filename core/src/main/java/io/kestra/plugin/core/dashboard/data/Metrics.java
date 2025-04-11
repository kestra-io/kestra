package io.kestra.plugin.core.dashboard.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Metric;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.dashboards.filters.EqualTo;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.core.repositories.QueryBuilderInterface;
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
@Schema(
    title = "Metrics are data exposed by tasks after execution.",
    description = "A chart using Metrics could display the number of rows loaded in a bigQuery task or an output count from a SQL Query; anything exposed by an execution." 
    )
@Plugin(
    examples = {
        @Example(
            title = "Display a chart with rows inserted by Namespace.",
            full = true,
            code = {
                "id: table_metrics\n" +
                "type: io.kestra.plugin.core.dashboard.chart.Table\n" +
                "chartOptions:\n" +
                  "displayName: Rows Inserted by Namespace\n" +
                "data:\n" +
                  "type: io.kestra.plugin.core.dashboard.data.Metrics\n" +
                  "columns:\n" +
                    "namespace:\n" +
                      "field: NAMESPACE\n" +
                    "inserted_rows:\n" +
                      "field: VALUE\n" +
                      "agg: SUM\n" +
                  "where:\n" +
                    "- field: NAME\n" +
                      "type: EQUAL_TO\n" +
                      "value: rows\n" +
                  "orderBy:\n" +
                    "- column: inserted_rows\n" +
                      "order: DESC\n"
            }
        )
    }
)
public class Metrics<C extends ColumnDescriptor<Metrics.Fields>> extends DataFilter<Metrics.Fields, C> {
    @Override
    public Class<? extends QueryBuilderInterface<Metrics.Fields>> repositoryClass() {
        return MetricRepositoryInterface.class;
    }

    @Override
    public void setGlobalFilter(List<QueryFilter> filters, ZonedDateTime startDate, ZonedDateTime endDate) {
        List<AbstractFilter<Fields>> where = this.getWhere() != null ? new ArrayList<>(this.getWhere()) : new ArrayList<>();

        if (filters == null) {
            return;
        }

        List<QueryFilter> namespaceFilters = filters.stream().filter(f -> f.field().equals(QueryFilter.Field.NAMESPACE)).toList();
        if (!namespaceFilters.isEmpty()) {
            where.removeIf(filter -> filter.getField().equals(Metrics.Fields.NAMESPACE));
            namespaceFilters.forEach(f -> {
                where.add(EqualTo.<Metrics.Fields>builder().field(Metrics.Fields.NAMESPACE).value(f.value()).build());
            });
        }

        this.setWhere(where);
    }


    public enum Fields {
        NAMESPACE,
        FLOW_ID,
        TASK_ID,
        EXECUTION_ID,
        TASK_RUN_ID,
        TYPE,
        NAME,
        VALUE,
        DATE
    }
}
