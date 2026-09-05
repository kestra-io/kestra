package io.kestra.webserver.controllers.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.store.TriggerStateStore;
import io.kestra.core.utils.TestsUtils;
import io.kestra.webserver.models.ChartFiltersOverrides;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.micronaut.http.HttpRequest.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the dashboards bundled with this edition. Storing dashboards of one's own is an Enterprise
 * feature, so its lifecycle is exercised by the Enterprise controller test instead.
 */
@KestraTest
class DashboardControllerTest {

    public static final String DASHBOARD_PATH = "/api/v1/main/dashboards";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    LogDataStoreInterface logRepository;

    @Inject
    ExecutionRepositoryInterface executionRepository;

    @Inject
    TriggerStateStore triggerStateStore;

    @Test
    void shouldExportAnAdHocPreviewChartToCsv() {
        var uuid = IdUtils.create();
        var fakeNamespace = "a-namespace_" + uuid;
        var logTimestamp = Instant.now();
        var fakeExecutionId = "an-execution-id" + uuid;
        logRepository.save(
            LogEntry.builder()
                .namespace(fakeNamespace)
                .level(Level.INFO)
                .attemptNumber(1)
                .executionId(fakeExecutionId)
                .tenantId(MAIN_TENANT)
                .executionKind(ExecutionKind.NORMAL)
                .flowId("a-flow-id")
                .timestamp(logTimestamp)
                .message("a message")
                .build()
        );

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
        byte[] csvBytes = client.toBlocking().retrieve(POST(DASHBOARD_PATH + "/charts/export", previewRequest), Argument.of(byte[].class));
        var csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csv).isEqualTo("chart_namespace,chart_execution_id\r\n%s,%s\r\n".formatted(fakeNamespace, fakeExecutionId));
    }

    @Test
    void shouldExportChartFromDefaultDashboardSentinel() {
        // the "_default" id is a reserved sentinel resolving to the built-in default dashboard, not a stored one
        DashboardController.DashboardResponse defaultDashboard = client.toBlocking().retrieve(
            GET(DASHBOARD_PATH + "/_default"),
            DashboardController.DashboardResponse.class
        );
        assertThat(defaultDashboard).isNotNull();
        assertThat(defaultDashboard.getId()).isEqualTo("_default");
        assertThat(defaultDashboard.getCharts()).hasSize(10);

        byte[] csvBytes = client.toBlocking().retrieve(
            POST(DASHBOARD_PATH + "/_default/charts/logs_timeseries/export", ChartFiltersOverrides.builder().filters(Collections.emptyList()).build()),
            Argument.of(byte[].class)
        );
        var csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csv).contains("date").contains("level").contains("total");
    }

    @Test
    void shouldTreatNullFiltersAsNoFiltersOnChartData() {
        // Raw JSON body with an explicit "filters":null, matching the reported repro exactly -
        // going through ChartFiltersOverrides' builder would drop the null key (NON_NULL inclusion)
        // and never exercise the bug. Export is the only route reaching the dashboard query builder
        // in this edition, charts being rendered from their definition instead.
        HttpResponse<byte[]> exported = client.toBlocking().exchange(
            POST(DASHBOARD_PATH + "/_default/charts/total_executions_timeseries/export", "{\"filters\":null}").contentType(MediaType.APPLICATION_JSON),
            Argument.of(byte[].class)
        );
        assertThat(exported.getStatus().getCode()).isEqualTo(200);
    }

    @Test
    void shouldReturnBuiltinDefaultDashboardDefinitions() {
        Map definitions = client.toBlocking().retrieve(
            GET(DASHBOARD_PATH + "/defaults/definitions"),
            Map.class
        );
        assertThat(definitions).containsOnlyKeys("main", "flow", "namespace");
        assertThat((String) definitions.get("main")).contains("kpi_success_ratio");
        assertThat((String) definitions.get("flow")).contains("--NAMESPACE--").contains("--FLOW--");
        assertThat((String) definitions.get("namespace")).contains("kpi_success_ratio");
    }

    @Test
    void shouldPreviewChartFilteredByLabels() {
        String namespace = TestsUtils.randomNamespace();
        executionRepository.save(
            Execution.builder()
                .tenantId(MAIN_TENANT)
                .id(IdUtils.create())
                .namespace(namespace)
                .flowId("flow")
                .state(new State())
                .labels(Label.from(Map.of("a", "b")))
                .build()
        );
        String idForLabelAC = IdUtils.create();
        executionRepository.save(
            Execution.builder()
                .tenantId(MAIN_TENANT)
                .id(idForLabelAC)
                .namespace(namespace)
                .flowId("flow")
                .state(new State())
                .labels(Label.from(Map.of("a", "c")))
                .build()
        );

        String chartYaml = """
            id: table_executions_chart_id
            type: io.kestra.plugin.core.dashboard.chart.Table
            data:
              type: io.kestra.plugin.core.dashboard.data.Executions
              columns:
                execution_id:
                  field: ID
              where:
                - field: NAMESPACE
                  type: EQUAL_TO
                  value: "%s"
            """.formatted(namespace);

        // Compute a dashboard, making sure the query is correct
        var previewRequest = new DashboardController.PreviewRequest(
            chartYaml, ChartFiltersOverrides.builder().filters(
                List.of(
                    QueryFilter.builder()
                        .field(QueryFilter.Field.LABELS)
                        .operation(QueryFilter.Op.EQUALS)
                        .value("a:c")
                        .build()
                )
            ).build()
        );
        
        PagedResults<Map<String, Object>> chartData = client.toBlocking().retrieve(
            POST(DASHBOARD_PATH + "/charts/preview", previewRequest),
            PagedResults.class
        );
        assertThat(chartData).isNotNull();
        assertThat(chartData.getTotal()).isEqualTo(1);
        assertThat(chartData.getResults().get(0).get("execution_id")).isEqualTo(idForLabelAC);
    }

    @Test
    void shouldExcludeDisabledTriggersFromNextExecutionsChart() {
        String namespace = TestsUtils.randomNamespace();

        String enabledTriggerId = IdUtils.create();
        triggerStateStore.save(
            TriggerState.builder()
                .tenantId(MAIN_TENANT)
                .namespace(namespace)
                .flowId("flow")
                .triggerId(enabledTriggerId)
                .workerId("worker")
                .nextEvaluationDate(Instant.now())
                .disabled(false)
                .build()
        );

        String disabledTriggerId = IdUtils.create();
        triggerStateStore.save(
            TriggerState.builder()
                .tenantId(MAIN_TENANT)
                .namespace(namespace)
                .flowId("flow")
                .triggerId(disabledTriggerId)
                .workerId("worker")
                .nextEvaluationDate(Instant.now())
                .disabled(true)
                .build()
        );

        String chartYaml = """
            id: table_next_executions_chart_id
            type: io.kestra.plugin.core.dashboard.chart.Table
            data:
              type: io.kestra.plugin.core.dashboard.data.Triggers
              columns:
                trigger_id:
                  field: TRIGGER_ID
              where:
                - field: NAMESPACE
                  type: EQUAL_TO
                  value: "%s"
                - field: DISABLED
                  type: EQUAL_TO
                  value: false
            """.formatted(namespace);

        var previewRequest = new DashboardController.PreviewRequest(chartYaml, null);

        PagedResults<Map<String, Object>> chartData = client.toBlocking().retrieve(
            POST(DASHBOARD_PATH + "/charts/preview", previewRequest),
            PagedResults.class
        );

        assertThat(chartData).isNotNull();
        assertThat(chartData.getTotal()).isEqualTo(1);
        assertThat(chartData.getResults().get(0).get("trigger_id")).isEqualTo(enabledTriggerId);
    }

    @Test
    void previewShouldRejectCatastrophicRegexInWhereClause() {
        // A REGEX filter embedded directly in an ad-hoc (non-persisted) chart definition must be
        // rejected before it ever reaches a repository backend — this endpoint bypasses the normal
        // dashboard create/update validation, so the chart itself must still be validated.
        String chartYaml = """
            id: table_executions_chart_id
            type: io.kestra.plugin.core.dashboard.chart.Table
            data:
              type: io.kestra.plugin.core.dashboard.data.Executions
              columns:
                execution_id:
                  field: ID
              where:
                - field: NAMESPACE
                  type: REGEX
                  value: "(a+)+"
            """;

        var previewRequest = new DashboardController.PreviewRequest(chartYaml, null);

        HttpClientResponseException httpClientResponseException = Assertions.assertThrows(
            HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                POST(DASHBOARD_PATH + "/charts/preview", previewRequest),
                PagedResults.class
            )
        );
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(422);
        assertThat(httpClientResponseException.getMessage()).contains("catastrophic backtracking");
    }

    @Test
    void shouldProcessDashboardIntervalRangeGreaterThanYear() {
        String namespace = TestsUtils.randomNamespace();
        executionRepository.save(
            Execution.builder()
                .tenantId(MAIN_TENANT)
                .id(IdUtils.create())
                .namespace(namespace)
                .flowId("flow")
                .state(new State(State.Type.SUCCESS))
                .build()
        );

        String chartYaml = """
            id: total_executions_timeseries
            type: io.kestra.plugin.core.dashboard.chart.TimeSeries
            chartOptions:
              description: Executions duration and count per date
              displayName: Total Executions
              legend:
                enabled: true
              column: date
              colorByColumn: state
              width: 8
            data:
              type: io.kestra.plugin.core.dashboard.data.Executions
              columns:
                date:
                  field: START_DATE
                  displayName: Date
                state:
                  field: STATE
                total:
                  displayName: Executions
                  agg: COUNT
                  graphStyle: BARS
                duration:
                  field: DURATION
                  displayName: Duration
                  agg: SUM
                  graphStyle: LINES
            """;

        ZonedDateTime currentTime = ZonedDateTime.now();

        List<QueryFilter> filters = List.of(
            QueryFilter.builder().field(QueryFilter.Field.START_DATE).value(currentTime.minusDays(2)).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(),
            QueryFilter.builder().field(QueryFilter.Field.END_DATE).value(currentTime.plusYears(2)).operation(Op.LESS_THAN_OR_EQUAL_TO).build()
        );

        var globalChartOptions = ChartFiltersOverrides.builder().filters(filters).build();

        var previewRequest = new DashboardController.PreviewRequest(chartYaml, globalChartOptions);

        var chartData = client.toBlocking().retrieve(
            POST(DASHBOARD_PATH + "/charts/preview", previewRequest),
            PagedResults.class
        );

        assertThat(chartData).isNotNull();
        assertThat(chartData.getTotal()).isGreaterThan(0L);
        assertThat(chartData.getResults()).isNotNull().isNotEmpty();
    }

    @Test
    void shouldReturnAnEmptyPageWhenSearchingDashboards() {
        PagedResults<?> results = client.toBlocking().retrieve(
            GET(DASHBOARD_PATH),
            PagedResults.class
        );

        assertThat(results.getTotal()).isZero();
        assertThat(results.getResults()).isEmpty();
    }

    @Test
    void shouldNotFindADashboardOtherThanTheBuiltInDefault() {
        HttpClientResponseException exception = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(GET(DASHBOARD_PATH + "/" + IdUtils.create()))
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(404);
    }

    @Test
    void shouldNotExposeDashboardCreation() {
        String dashboardYaml = """
            id: a-dashboard
            title: A dashboard
            charts:
              - id: a_chart
                type: io.kestra.plugin.core.dashboard.chart.Markdown
                chartOptions:
                  displayName: A chart
                source:
                  type: io.kestra.plugin.core.dashboard.chart.mardown.sources.Text
                  content: hello
            """;

        HttpClientResponseException exception = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(POST(DASHBOARD_PATH, dashboardYaml).contentType(MediaType.APPLICATION_YAML))
        );

        assertThat(exception.getStatus().getCode()).isIn(404, 405);
    }
}
