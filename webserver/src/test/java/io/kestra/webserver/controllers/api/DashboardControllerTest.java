package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.DashboardRepositoryInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.models.ChartFiltersOverrides;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.micronaut.http.HttpRequest.*;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class DashboardControllerTest {

    public static final String DASHBOARD_PATH = "/api/v1/main/dashboards";
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    LogRepositoryInterface logRepository;

    @Inject
    DashboardRepositoryInterface dashboardRepository;

    @Test
    void full() throws JsonProcessingException {
        String dashboardYaml = """
            id: full
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
        assertThat(dashboard.getId()).isEqualTo("full");
        assertThat(dashboard.getTitle()).isEqualTo("Some Dashboard");
        assertThat(dashboard.getDescription()).isEqualTo("Default overview dashboard");

        // Get a dashboard
        Dashboard get = client.toBlocking().retrieve(
            GET(DASHBOARD_PATH + "/" + dashboard.getId()),
            Dashboard.class
        );
        assertThat(get).isNotNull();
        assertThat(get.getId()).isEqualTo(dashboard.getId());
        assertThat(get.getSourceCode()).startsWith("""
            id: full
            title: Some Dashboard""");

        // List dashboards
        List<Dashboard> dashboards = client.toBlocking().retrieve(
            GET(DASHBOARD_PATH),
            Argument.listOf(Dashboard.class)
        );
        assertThat(dashboards).hasSize(1);

        // Compute a dashboard
        List<Map> chartData = client.toBlocking().retrieve(
            POST(DASHBOARD_PATH + "/" + dashboard.getId() + "/charts/logs_timeseries", ChartFiltersOverrides.builder().filters(Collections.emptyList()).build()),
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

    // The goal is to cover the legacy implementation that was autogenerating id so it was present on the backend but the source code didn't contain it.
    // We now mandate the id within the dashboard source code and if it's not yet there, the "get" API should add it to the existing source if it's not there so that it's added on the next save.
    @Test
    void sourceShouldHaveIdAddedIfNotPresent() throws JsonProcessingException {
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

        String dashboardId = "sourceShouldHaveIdAddedIfNotPresent";
        dashboardRepository.save(JacksonMapper.ofYaml().readValue(dashboardYaml, Dashboard.class).toBuilder().tenantId(TenantService.MAIN_TENANT).id(dashboardId).build(), dashboardYaml);

        Dashboard repositoryDashboard = dashboardRepository.get(TenantService.MAIN_TENANT, dashboardId).get();
        assertThat(repositoryDashboard.getId()).isEqualTo(dashboardId);
        assertThat(repositoryDashboard.getSourceCode()).doesNotContain("id: " + dashboardId);

        // Get a dashboard
        Dashboard get = client.toBlocking().retrieve(
            GET(DASHBOARD_PATH + "/" + dashboardId),
            Dashboard.class
        );
        assertThat(get).isNotNull();
        assertThat(get.getId()).isEqualTo(dashboardId);
        assertThat(get.getSourceCode()).contains("id: " + dashboardId);
    }

    @Test
    void cantHaveMultipleDashboardsWithSameId() {
        String dashboardYaml = """
            id: cantHaveMultipleDashboardsWithSameId
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

        client.toBlocking().retrieve(
            POST(DASHBOARD_PATH, dashboardYaml).contentType(MediaType.APPLICATION_YAML),
            Dashboard.class
        );

        HttpClientResponseException httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            POST(DASHBOARD_PATH, dashboardYaml).contentType(MediaType.APPLICATION_YAML),
            Dashboard.class
        ));
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(422);
        assertThat(httpClientResponseException.getMessage()).isEqualTo("Invalid entity: dashboard.id: Dashboard id already exists");
    }

    @Test
    void update() {
        String dashboardYaml = """
            id: update
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

        Dashboard get = client.toBlocking().retrieve(
            GET(DASHBOARD_PATH + "/" + dashboard.getId()),
            Dashboard.class
        );
        assertThat(get).isNotNull();
        assertThat(dashboard.getDescription()).isEqualTo("Default overview dashboard");

        // Update a dashboard
        dashboard = client.toBlocking().retrieve(
            PUT(DASHBOARD_PATH + "/" + dashboard.getId(), dashboardYaml.replace("Default overview dashboard", "Another description")).contentType(MediaType.APPLICATION_YAML),
            Dashboard.class
        );
        assertThat(dashboard).isNotNull();

        get = client.toBlocking().retrieve(
            GET(DASHBOARD_PATH + "/" + dashboard.getId()),
            Dashboard.class
        );
        assertThat(get).isNotNull();
        assertThat(dashboard.getDescription()).isEqualTo("Another description");

        Dashboard finalDashboard = dashboard;
        HttpClientResponseException httpStatusException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            PUT(DASHBOARD_PATH + "/" + finalDashboard.getId(), dashboardYaml.replace(finalDashboard.getId(), finalDashboard.getId() + "-updated")).contentType(MediaType.APPLICATION_YAML)
            , Dashboard.class));
        assertThat(httpStatusException.getStatus().getCode()).isEqualTo(422);
        assertThat(httpStatusException.getMessage()).isEqualTo("Invalid entity: dashboard.id: Illegal dashboard id update");

        get = client.toBlocking().retrieve(
            GET(DASHBOARD_PATH + "/" + dashboard.getId()),
            Dashboard.class
        );
        assertThat(get).isNotNull();
        assertThat(dashboard.getSourceCode()).contains("id: " + dashboard.getId());
        assertThat(dashboard.getDescription()).isEqualTo("Another description");
    }

    @Test
    void mandatoryId() {
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
        HttpClientResponseException httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            POST(DASHBOARD_PATH, dashboardYaml).contentType(MediaType.APPLICATION_YAML),
            Dashboard.class
        ));
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(422);
        assertThat(httpClientResponseException.getMessage()).isEqualTo("Illegal argument: Dashboard id is mandatory");
    }

    @Test
    void exportACustomDashboardChartToCsv() {
        var uuid = IdUtils.create();
        var fakeNamespace = "a-namespace_" + uuid;
        var logTimestamp = Instant.now();
        var fakeExecutionId = "an-execution-id" + uuid;
        logRepository.save(LogEntry.builder()
            .namespace(fakeNamespace)
            .level(Level.INFO)
            .attemptNumber(1)
            .deleted(false)
            .executionId(fakeExecutionId)
            .tenantId(MAIN_TENANT)
            .executionKind(ExecutionKind.NORMAL)
            .flowId("a-flow-id")
            .timestamp(logTimestamp)
            .message("a message")
            .build());

        String dashboardYaml = """
            id: exportACustomDashboardChartToCsv
            title: A dashboard with a simple table
            timeWindow:
              default: P30D # P30DT30H
              max: P365D
            charts:
              - id: table_logs_chart_id
                type: io.kestra.plugin.core.dashboard.chart.Table
                data:
                  type: io.kestra.plugin.core.dashboard.data.Logs
                  columns:
                    chart_namespace:
                      field: NAMESPACE
                    chart_execution_id:
                      field: EXECUTION_ID
                  where:
                    - field: NAMESPACE
                      type: EQUAL_TO
                      value: "%s"
                    - field: EXECUTION_ID
                      type: EQUAL_TO
                      value: "%s"
            """.formatted(fakeNamespace, fakeExecutionId);

        // Create a dashboard
        Dashboard dashboard = client.toBlocking().retrieve(
            POST(DASHBOARD_PATH, dashboardYaml).contentType(MediaType.APPLICATION_YAML),
            Dashboard.class
        );
        assertThat(dashboard).isNotNull();
        assertThat(dashboard.getId()).isNotNull();
        assertThat(dashboard.getTitle()).isEqualTo("A dashboard with a simple table");

        // Compute a dashboard, making sure the query is correct
        PagedResults<Map<String, Object>> chartData = client.toBlocking().retrieve(
            POST(DASHBOARD_PATH + "/" + dashboard.getId() + "/charts/table_logs_chart_id", ChartFiltersOverrides.builder().filters(Collections.emptyList()).build()),
            PagedResults.class
        );
        assertThat(chartData).isNotNull();
        assertThat(chartData.getTotal()).isEqualTo(1);
        assertThat(chartData.getResults().get(0).get("chart_namespace")).isEqualTo(fakeNamespace);
        assertThat(chartData.getResults().get(0).get("chart_execution_id")).isEqualTo(fakeExecutionId);

        // export CSV
        byte[] csvBytes = client.toBlocking().retrieve(POST(DASHBOARD_PATH + "/" + dashboard.getId() + "/charts/table_logs_chart_id/export/to-csv", ChartFiltersOverrides.builder().filters(Collections.emptyList()).build()), Argument.of(byte[].class));
        var csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csv).isEqualTo("chart_namespace,chart_execution_id\r\n%s,%s\r\n".formatted(fakeNamespace, fakeExecutionId));
    }

    @Test
    void exportADefaultDashboardChartToCsv() {
        var uuid = IdUtils.create();
        var fakeNamespace = "a-namespace_" + uuid;
        var logTimestamp = Instant.now();
        var fakeExecutionId = "an-execution-id" + uuid;
        logRepository.save(LogEntry.builder()
            .namespace(fakeNamespace)
            .level(Level.INFO)
            .attemptNumber(1)
            .deleted(false)
            .executionId(fakeExecutionId)
            .tenantId(MAIN_TENANT)
            .executionKind(ExecutionKind.NORMAL)
            .flowId("a-flow-id")
            .timestamp(logTimestamp)
            .message("a message")
            .build());

        String chartYaml = """
            id: table_logs_chart_id
            type: io.kestra.plugin.core.dashboard.chart.Table
            data:
              type: io.kestra.plugin.core.dashboard.data.Logs
              columns:
                chart_namespace:
                  field: NAMESPACE
                chart_execution_id:
                  field: EXECUTION_ID
              where:
                - field: NAMESPACE
                  type: EQUAL_TO
                  value: "%s"
                - field: EXECUTION_ID
                  type: EQUAL_TO
                  value: "%s"
            """.formatted(fakeNamespace, fakeExecutionId);

        // Compute a dashboard, making sure the query is correct
        var previewRequest = new DashboardController.PreviewRequest(chartYaml, ChartFiltersOverrides.builder().filters(Collections.emptyList()).build());
        PagedResults<Map<String, Object>> chartData = client.toBlocking().retrieve(
            POST(DASHBOARD_PATH + "/charts/preview", previewRequest),
            PagedResults.class
        );
        assertThat(chartData).isNotNull();
        assertThat(chartData.getTotal()).isEqualTo(1);
        assertThat(chartData.getResults().get(0).get("chart_namespace")).isEqualTo(fakeNamespace);
        assertThat(chartData.getResults().get(0).get("chart_execution_id")).isEqualTo(fakeExecutionId);

        // export CSV
        byte[] csvBytes = client.toBlocking().retrieve(POST(DASHBOARD_PATH + "/charts/export/to-csv", previewRequest), Argument.of(byte[].class));
        var csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csv).isEqualTo("chart_namespace,chart_execution_id\r\n%s,%s\r\n".formatted(fakeNamespace, fakeExecutionId));
    }
}
