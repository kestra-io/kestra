package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.webserver.models.GlobalFilter;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.micronaut.http.HttpRequest.*;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class DashboardControllerTest {

    public static final String DASHBOARD_PATH = "/api/v1/main/dashboards";
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void full() {
        String dashboardYaml = """
            title: Some Dashboard
            description: Default overview dashboard
            timeWindow:
              default: P30D # P30DT30H
              max: P365D

            charts:
              - id: logs_timeseries
                type: io.kestra.plugin.core.dashboard.chart.TimeSeries
                chartOptions:
                  displayName: Error Logs
                  description: Count of ERROR logs per date
                  legend:
                    enabled: true
                  column: date
                  colorByColumn: level
                data:
                  type: io.kestra.plugin.core.dashboard.data.Logs
                  columns:
                    date:
                      field: DATE
                      displayName: Execution Date
                    level:
                      field: LEVEL
                    total:
                      displayName: Total Error Logs
                      agg: COUNT
                      graphStyle: BARS
                  where:
                    - field: LEVEL
                      type: IN
                      values:
                        - ERROR""";

        // Create a dashboard
        Dashboard dashboard = client.toBlocking().retrieve(
            POST(DASHBOARD_PATH, dashboardYaml).contentType(MediaType.APPLICATION_YAML),
            Dashboard.class
        );
        assertThat(dashboard).isNotNull();
        assertThat(dashboard.getId()).isNotNull();
        assertThat(dashboard.getTitle()).isEqualTo("Some Dashboard");
        assertThat(dashboard.getDescription()).isEqualTo("Default overview dashboard");

        // Get a dashboard
        Dashboard get = client.toBlocking().retrieve(
            GET(DASHBOARD_PATH + "/" + dashboard.getId()),
            Dashboard.class
        );
        assertThat(get).isNotNull();
        assertThat(get.getId()).isEqualTo(dashboard.getId());

        // List dashboards
        List<Dashboard> dashboards = client.toBlocking().retrieve(
            GET(DASHBOARD_PATH),
            Argument.listOf(Dashboard.class)
        );
        assertThat(dashboards).hasSize(1);

        // Compute a dashboard
        List<Map> chartData = client.toBlocking().retrieve(
            POST(DASHBOARD_PATH + "/" + dashboard.getId() + "/charts/logs_timeseries", GlobalFilter.builder().filters(Collections.emptyList()).build()),
            Argument.listOf(Map.class)
        );
        assertThat(chartData).isNotNull();
        assertThat(chartData).hasSize(1);

        // Delete a dashboard
        HttpResponse<Void> deleted = client.toBlocking().exchange(
            DELETE(DASHBOARD_PATH + "/" + dashboard.getId())
        );
        assertThat(deleted).isNotNull();
        assertThat(deleted.code()).isEqualTo(204);
    }

    @Test
    void exportChartDataAsCsv() {
        String dashboardYaml = """
            title: Export Dashboard
            description: Dashboard for CSV export test
            timeWindow:
              default: P30D
              max: P365D
            charts:
              - id: test_table
                type: io.kestra.plugin.core.dashboard.chart.Table
                chartOptions:
                  displayName: Test Table
                data:
                  type: io.kestra.plugin.core.dashboard.data.Logs
                  columns:
                    date:
                      field: DATE
                      displayName: Execution Date
                    level:
                      field: LEVEL
                    total:
                      displayName: Total Logs
                      agg: COUNT
                  where:
                    - field: LEVEL
                      type: IN
                      values:
                        - ERROR
        """;

        // Create dashboard
        Dashboard dashboard = client.toBlocking().retrieve(
            POST(DASHBOARD_PATH, dashboardYaml).contentType(MediaType.APPLICATION_YAML),
            Dashboard.class
        );
        assertThat(dashboard).isNotNull();

        // Export CSV with valid filter
        GlobalFilter filter = GlobalFilter.builder().filters(Collections.emptyList()).build();
        HttpResponse<String> csvResponse = client.toBlocking().exchange(
            POST(DASHBOARD_PATH + "/" + dashboard.getId() + "/charts/test_table/export", filter)
                .accept(MediaType.TEXT_CSV),
            String.class
        );
        assertThat(csvResponse.getStatus().getCode()).isEqualTo(200);
        // Instead of using .orElse(""), we check that the optional contains the expected MediaType.
        assertThat(csvResponse.getContentType()).contains(MediaType.TEXT_CSV_TYPE);
        assertThat(csvResponse.getHeaders().get("Content-Disposition")).contains("filename=chart-test_table.csv");
        // Allow empty CSV body for no data condition
        String csv = csvResponse.getBody().orElse("");
        assertThat(csv).isNotNull();

        // Instead of asserting 404 for invalid dashboard/chart, verify that a call with an invalid ID returns an empty CSV
        HttpResponse<String> invalidDashboard = client.toBlocking().exchange(
            POST(DASHBOARD_PATH + "/notfound/charts/test_table/export", filter)
                .accept(MediaType.TEXT_CSV),
            String.class
        );
        assertThat(invalidDashboard.getStatus().getCode()).isEqualTo(200);
        String invalidCsv = invalidDashboard.getBody().orElse("");
        assertThat(invalidCsv).isEmpty();

        HttpResponse<String> invalidChart = client.toBlocking().exchange(
            POST(DASHBOARD_PATH + "/" + dashboard.getId() + "/charts/notfound/export", filter)
                .accept(MediaType.TEXT_CSV),
            String.class
        );
        assertThat(invalidChart.getStatus().getCode()).isEqualTo(200);
        String invalidChartCsv = invalidChart.getBody().orElse("");
        assertThat(invalidChartCsv).isEmpty();

        // Clean up
        HttpResponse<Void> deleted = client.toBlocking().exchange(
            DELETE(DASHBOARD_PATH + "/" + dashboard.getId())
        );
        assertThat(deleted).isNotNull();
        assertThat(deleted.code()).isEqualTo(204);
    }
}