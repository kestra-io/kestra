package io.kestra.plugin.core.dashboard.chart;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.dashboards.filters.In;
import io.kestra.core.models.flows.State;
import io.kestra.plugin.core.dashboard.chart.tables.TableColumnDescriptor;
import io.kestra.plugin.core.dashboard.chart.tables.TableOption;
import io.kestra.plugin.core.dashboard.data.Executions;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@EqualsAndHashCode(callSuper = true)
@Schema(
    title = "List recent failed executions, scoped to the dashboard's filters.",
    description = "Always restricts results to executions in the FAILED state. Inherits sorting, columns and pagination from the standard Table chart options. The chart is contract-bound to `io.kestra.plugin.core.dashboard.data.Executions` data."
)
@Plugin(
    examples = {
        @Example(
            title = "Display the recent failed executions for a flow.",
            full = true,
            code = """
                charts:
                  - id: recent_failures
                    type: io.kestra.plugin.core.dashboard.chart.FailedExecutions
                    chartOptions:
                      displayName: Recent failures
                      pagination:
                        enabled: true
                    data:
                      type: io.kestra.plugin.core.dashboard.data.Executions
                      columns:
                        id:
                          field: ID
                          displayName: Execution ID
                        flowId:
                          field: FLOW_ID
                          displayName: Flow
                        state:
                          field: STATE
                          displayName: State
                        startDate:
                          field: START_DATE
                          displayName: Start Date
                        duration:
                          field: DURATION
                          displayName: Duration
                """
        )
    }
)
public class FailedExecutions<F extends Enum<F>, D extends DataFilter<F, ? extends TableColumnDescriptor<F>>> extends DataChart<TableOption, D> {

    // Cached instance with the FAILED state filter pre-applied.
    // The dashboard pipeline (DashboardController + AbstractJdbcDashboardRepository)
    // calls getData() multiple times per request and mutates the result via
    // updateWhereWithGlobalFilters. Returning a fresh copy each call would lose
    // those mutations; mutating the underlying data field would leak across
    // requests if a Dashboard ever got cached. Caching one copy per chart
    // instance keeps both invariants without touching shared state.
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    private transient D modifiedData;

    @Override
    public D getData() {
        if (modifiedData != null) {
            return modifiedData;
        }
        D data = super.getData();
        if (data == null) {
            return null;
        }
        if (data instanceof Executions<?> executions) {
            @SuppressWarnings("unchecked")
            D copy = (D) executions.toBuilder()
                .where(withFailedStateFilter(executions.getWhere()))
                .build();
            modifiedData = copy;
        } else {
            modifiedData = data;
        }
        return modifiedData;
    }

    private static List<AbstractFilter<Executions.Fields>> withFailedStateFilter(List<AbstractFilter<Executions.Fields>> existing) {
        List<AbstractFilter<Executions.Fields>> result = new ArrayList<>();
        if (existing != null) {
            existing.stream()
                .filter(filter -> !Executions.Fields.STATE.equals(filter.getField()))
                .forEach(result::add);
        }
        result.add(In.<Executions.Fields>builder()
            .field(Executions.Fields.STATE)
            .values(List.<Object>of(State.Type.FAILED.name()))
            .build());
        return result;
    }
}
