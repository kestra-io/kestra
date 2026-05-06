package io.kestra.plugin.core.dashboard.chart;

import java.util.List;
import java.util.Map;

import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.dashboards.filters.In;
import io.kestra.core.models.flows.State;
import io.kestra.plugin.core.dashboard.chart.tables.TableColumnDescriptor;
import io.kestra.plugin.core.dashboard.chart.tables.TableOption;
import io.kestra.plugin.core.dashboard.data.Executions;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailedExecutionsTest {

    @Test
    void shouldAppendFailedStateFilterWhenWhereIsAbsent() {
        FailedExecutions<Executions.Fields, Executions<TableColumnDescriptor<Executions.Fields>>> chart =
            buildChart(buildExecutions(null));

        List<AbstractFilter<Executions.Fields>> where = chart.getData().getWhere();

        assertOnlyFailedStateFilter(where);
    }

    @Test
    void shouldPreserveNonStateFiltersAndForceFailed() {
        AbstractFilter<Executions.Fields> namespaceFilter = In.<Executions.Fields>builder()
            .field(Executions.Fields.NAMESPACE)
            .values(List.<Object>of("io.kestra.tests"))
            .build();

        FailedExecutions<Executions.Fields, Executions<TableColumnDescriptor<Executions.Fields>>> chart =
            buildChart(buildExecutions(List.of(namespaceFilter)));

        List<AbstractFilter<Executions.Fields>> where = chart.getData().getWhere();

        assertThat(where).hasSize(2);
        assertThat(where.get(0)).isEqualTo(namespaceFilter);
        assertThat(where.get(1).getField()).isEqualTo(Executions.Fields.STATE);
    }

    @Test
    void shouldOverrideUserSuppliedStateFilter() {
        AbstractFilter<Executions.Fields> userStateFilter = In.<Executions.Fields>builder()
            .field(Executions.Fields.STATE)
            .values(List.<Object>of(State.Type.SUCCESS.name()))
            .build();

        FailedExecutions<Executions.Fields, Executions<TableColumnDescriptor<Executions.Fields>>> chart =
            buildChart(buildExecutions(List.of(userStateFilter)));

        assertOnlyFailedStateFilter(chart.getData().getWhere());
    }

    @Test
    void shouldBeIdempotentAcrossMultipleGetDataCalls() {
        AbstractFilter<Executions.Fields> namespaceFilter = In.<Executions.Fields>builder()
            .field(Executions.Fields.NAMESPACE)
            .values(List.<Object>of("io.kestra.tests"))
            .build();

        FailedExecutions<Executions.Fields, Executions<TableColumnDescriptor<Executions.Fields>>> chart =
            buildChart(buildExecutions(List.of(namespaceFilter)));

        List<AbstractFilter<Executions.Fields>> first = chart.getData().getWhere();
        List<AbstractFilter<Executions.Fields>> second = chart.getData().getWhere();
        List<AbstractFilter<Executions.Fields>> third = chart.getData().getWhere();

        assertThat(first).hasSize(2);
        assertThat(second).hasSize(2);
        assertThat(third).hasSize(2);
        assertThat(third.get(1).getField()).isEqualTo(Executions.Fields.STATE);
    }

    private static FailedExecutions<Executions.Fields, Executions<TableColumnDescriptor<Executions.Fields>>> buildChart(
        Executions<TableColumnDescriptor<Executions.Fields>> data
    ) {
        return FailedExecutions.<Executions.Fields, Executions<TableColumnDescriptor<Executions.Fields>>>builder()
            .id("recent_failures")
            .type("io.kestra.plugin.core.dashboard.chart.FailedExecutions")
            .chartOptions(TableOption.builder().displayName("Recent failures").build())
            .data(data)
            .build();
    }

    private static Executions<TableColumnDescriptor<Executions.Fields>> buildExecutions(List<AbstractFilter<Executions.Fields>> where) {
        Executions.ExecutionsBuilder<TableColumnDescriptor<Executions.Fields>, ?, ?> builder =
            Executions.<TableColumnDescriptor<Executions.Fields>>builder()
                .type("io.kestra.plugin.core.dashboard.data.Executions")
                .columns(Map.of(
                    "id", TableColumnDescriptor.<Executions.Fields>builder()
                        .field(Executions.Fields.ID)
                        .build()
                ));

        if (where != null) {
            builder.where(where);
        }

        return builder.build();
    }

    private static void assertOnlyFailedStateFilter(List<AbstractFilter<Executions.Fields>> where) {
        assertThat(where).hasSize(1);
        AbstractFilter<Executions.Fields> only = where.get(0);
        assertThat(only.getField()).isEqualTo(Executions.Fields.STATE);
        assertThat(only).isInstanceOf(In.class);
        assertThat(((In<?>) only).getValues()).containsExactly(State.Type.FAILED.name());
    }
}
