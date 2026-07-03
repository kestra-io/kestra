package io.kestra.core.repositories;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.core.models.dashboards.charts.DataChartKPI;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.dashboard.chart.KPI;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public abstract class AbstractDashboardRepositoryTest {

    @Inject
    protected DashboardRepositoryInterface dashboardRepositoryInterface;

    private final String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

    protected Dashboard createDashboard(String tenantId, String title) {
        return Dashboard.builder().id(IdUtils.create()).title(title).tenantId(tenantId).build();

    }

    @Test
    void saveAndGetDashboard() {
        Dashboard dashboard = createDashboard(tenant, "Test Dashboard");

        dashboardRepositoryInterface.save(dashboard, "test");

        Optional<Dashboard> found = dashboardRepositoryInterface.get(tenant, dashboard.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Test Dashboard");
    }

    @Test
    void shouldFindAllDashboards() {
        Dashboard dashboard1 = createDashboard(tenant, "Dashboard 1");
        Dashboard dashboard2 = createDashboard(tenant, "Dashboard 2");

        dashboardRepositoryInterface.save(dashboard1, "test");
        dashboardRepositoryInterface.save(dashboard2, "test");

        List<Dashboard> dashboards = dashboardRepositoryInterface.findAll(tenant);
        assertThat(dashboards).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldListDashboards() {
        Dashboard dashboard = createDashboard(tenant, "Dashboard List");
        dashboardRepositoryInterface.save(dashboard, "test");
        ArrayListTotal<Dashboard> result = dashboardRepositoryInterface.list(Pageable.UNPAGED, tenant, "dashboard");
        assertThat(result.getTotal()).isGreaterThan(0);
    }

    @Test
    void shouldDeleteDashboard() {
        Dashboard dashboard = createDashboard(tenant, "delete-me");

        dashboardRepositoryInterface.save(dashboard, "test");

        dashboardRepositoryInterface.delete(tenant, dashboard.getId());

        Optional<Dashboard> found = dashboardRepositoryInterface.get(tenant, dashboard.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void shouldCountDashboards() {
        long before = dashboardRepositoryInterface.countAllForAllTenants();

        Dashboard dashboard = createDashboard(tenant, "Test Dashboard");

        dashboardRepositoryInterface.save(dashboard, "test");

        long after = dashboardRepositoryInterface.countAllForAllTenants();

        assertThat(after).isGreaterThan(before);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void generate_shouldReturnExpectedRows() throws IOException {
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
        ArrayListTotal<Map<String, Object>> result = dashboardRepositoryInterface.generate(tenant, chart, ZonedDateTime.now().minusDays(30), ZonedDateTime.now(), Pageable.UNPAGED);
        assertThat(result).isNotNull();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void generateKPI_shouldReturnExpectedValue() throws Exception {

        DataChartKPI chart = YamlParser.parse("""
                    id: KPI_SUCCESS_PERCENTAGE
                    type: io.kestra.plugin.core.dashboard.chart.KPI # io.kestra.plugin.core.dashboard.chart.Trends
                    chartOptions:
                      displayName: Success Ratio
                      numberType: PERCENTAGE
                      width: 3
                    data:
                      type: io.kestra.plugin.core.dashboard.data.ExecutionsKPI # io.kestra.plugin.core.dashboard.data.ExecutionsTrends
                      columns:
                        field: FLOW_ID
                        agg: COUNT
                      numerator:
                        - field: STATE
                          type: IN
                          values:
                            - SUCCESS
                      where: # optional if you filter by namespace
                        - field: NAMESPACE
                          type: EQUAL_TO
                          value: "company.team"
            """, KPI.class);

        List<Map<String, Object>> result = dashboardRepositoryInterface.generateKPI(tenant, chart, ZonedDateTime.now().minusDays(30), ZonedDateTime.now());

        assertThat(result).isNotEmpty();
    }


}
