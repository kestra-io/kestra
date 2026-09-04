package io.kestra.core.services;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.core.models.dashboards.charts.DataChartKPI;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.dashboard.chart.KPI;

import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The aggregation each chart resolves to is built per backend, so every backend runs these.
 */
@KestraTest
public abstract class AbstractChartDataServiceTest {

    @Inject
    protected ChartDataService chartDataService;

    private final String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void shouldReturnRowsWhenGeneratingADataChart() throws IOException {
        DataChart chart = (DataChart) YamlParser.parse("""
                             id: table_logs
                             type: io.kestra.plugin.core.dashboard.chart.Table
                             chartOptions:
                               displayName: Log count by level for filtered namespace
                             data:
                               type: io.kestra.plugin.core.dashboard.data.Logs
                               columns:
                                 level:
                                   field: LEVEL
                                   agg: COUNT
                               where:
                                 - field: NAMESPACE
                                   type: IN
                                   values:
                                     - dev_graph
                                     - prod_graph
            """, Chart.class);

        ArrayListTotal<Map<String, Object>> result = chartDataService.generate(tenant, chart, ZonedDateTime.now().minusDays(30), ZonedDateTime.now(), Pageable.UNPAGED);

        assertThat(result).isNotNull();
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void shouldReturnAValueWhenGeneratingAKpiChart() throws Exception {
        DataChartKPI chart = YamlParser.parse("""
                    id: KPI_SUCCESS_PERCENTAGE
                    type: io.kestra.plugin.core.dashboard.chart.KPI
                    chartOptions:
                      displayName: Success Ratio
                      numberType: PERCENTAGE
                      width: 3
                    data:
                      type: io.kestra.plugin.core.dashboard.data.ExecutionsKPI
                      columns:
                        field: FLOW_ID
                        agg: COUNT
                      numerator:
                        - field: STATE
                          type: IN
                          values:
                            - SUCCESS
                      where:
                        - field: NAMESPACE
                          type: EQUAL_TO
                          value: "company.team"
            """, KPI.class);

        List<Map<String, Object>> result = chartDataService.generateKPI(tenant, chart, ZonedDateTime.now().minusDays(30), ZonedDateTime.now());

        assertThat(result).isNotEmpty();
    }
}
