package io.kestra.webserver.controllers.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.models.Label;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.models.dashboards.ExportFormat;
import io.kestra.core.models.dashboards.TimeWindow;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.core.models.dashboards.charts.DataChartKPI;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.services.ChartDataService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.validations.TenantId;
import io.kestra.plugin.core.dashboard.chart.Markdown;
import io.kestra.plugin.core.dashboard.chart.mardown.sources.FlowDescription;
import io.kestra.webserver.models.ChartFiltersOverrides;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.CSVUtils;
import io.kestra.webserver.utils.PageableUtils;
import io.kestra.webserver.utils.TimeLineSearch;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import static io.kestra.core.utils.DateUtils.validateTimeline;

/**
 * Serves the dashboards bundled with the edition. Storing and editing dashboards of one's own is an
 * Enterprise feature, so the persistence-backed operations are added by the Enterprise subclass; this
 * one resolves {@link Dashboard#DEFAULT_DASHBOARD_ID} and nothing else.
 * <p>
 * The by-id read and export routes are kept here because {@code io.kestra.plugin.kestra.dashboards.Export}
 * defaults its {@code dashboardId} to that sentinel, so the task has to work against an instance that
 * stores no dashboards. Charts are rendered from a definition through {@code charts/preview} instead,
 * which is why no by-id chart-data route is served here.
 */
@Controller("/api/v1/{tenant}/dashboards")
@Slf4j
public class DashboardController {
    private static final String DEFAULT_MAIN_DEFINITION_RESOURCE = "dashboards/default_main_definition.yaml";
    private static final String DEFAULT_FLOW_DEFINITION_RESOURCE = "dashboards/default_flow_definition.yaml";
    private static final String DEFAULT_NAMESPACE_DEFINITION_RESOURCE = "dashboards/default_namespace_definition.yaml";

    // Bundled resources never change at runtime, read them once instead of on every call
    private static final Map<String, String> DEFAULT_DASHBOARD_DEFINITIONS = Map.of(
        "main", Dashboard.readClasspathResource(DEFAULT_MAIN_DEFINITION_RESOURCE),
        "flow", Dashboard.readClasspathResource(DEFAULT_FLOW_DEFINITION_RESOURCE),
        "namespace", Dashboard.readClasspathResource(DEFAULT_NAMESPACE_DEFINITION_RESOURCE)
    );

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ChartDataService chartDataService;

    @Inject
    protected TenantService tenantService;

    @Inject
    protected ModelValidator modelValidator;

    /**
     * Resolves the dashboard a read operation applies to. Only the bundled default is known here;
     * the Enterprise subclass widens this to the dashboards a tenant has stored.
     */
    protected Optional<Dashboard> findDashboard(String tenantId, String id) {
        return Dashboard.DEFAULT_DASHBOARD_ID.equals(id)
            ? Optional.of(Dashboard.defaultDashboard(tenantId))
            : Optional.empty();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get
    @Operation(tags = { "Dashboards" }, summary = "Search for dashboards")
    public PagedResults<DashboardResponse> searchDashboards(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) @Max(PageableUtils.MAX_PAGE_SIZE) int size,
        @Parameter(description = "The filter query") @Nullable @QueryValue String q,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort) throws ConstraintViolationException {
        return PagedResults.of(new ArrayListTotal<>(List.of(), 0));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{id}")
    @Operation(tags = { "Dashboards" }, summary = "Get a dashboard")
    public DashboardResponse getDashboard(
        @Parameter(description = "The dashboard id") @PathVariable String id) throws ConstraintViolationException {
        return findDashboard(tenantService.resolveTenant(), id)
            .map(DashboardResponse::new)
            .orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "defaults/definitions")
    @Operation(tags = { "Dashboards" }, summary = "Get the built-in default dashboard definitions")
    public Map<String, String> getDefaultDashboardDefinitions() {
        return DEFAULT_DASHBOARD_DEFINITIONS;
    }

    protected FetchChartDataQuery buildDashboardChardDataQuery(String id, String chartId, ChartFiltersOverrides globalFilter) {
        String tenantId = tenantService.resolveTenant();
        List<QueryFilter> filters = globalFilter.getFilters();

        filters = formatLabelsFilters(filters);

        Dashboard dashboard = findDashboard(tenantId, id).orElse(null);
        if (dashboard == null) {
            return null;
        }

        TimeLineSearch timeLineSearch = TimeLineSearch.extractFrom(filters != null ? filters : List.of());
        validateTimeline(timeLineSearch.getStartDate(), timeLineSearch.getEndDate());

        ZonedDateTime endDate = timeLineSearch.getEndDate();
        ZonedDateTime startDate = timeLineSearch.getStartDate();

        if (startDate == null) {
            // If no start date is provided, we use the default duration of the dashboard's time
            startDate = endDate.minus(dashboard.getTimeWindow().getDefaultDuration());
        }

        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("`endDate` must be after `startDate`.");
        }

        Duration windowDuration = Duration.ofSeconds((endDate != null ? endDate : ZonedDateTime.now()).minus(Duration.ofSeconds(startDate.toEpochSecond())).toEpochSecond());
        if (windowDuration.compareTo(dashboard.getTimeWindow().getMax()) > 0) {
            throw new IllegalArgumentException("The queried window is larger than the max allowed one.");
        }

        Chart<?> chart = dashboard.getCharts().stream().filter(g -> g.getId().equals(chartId)).findFirst().orElse(null);
        if (chart == null) {
            return null;
        }
        var pageNumber = globalFilter.getPageNumber();
        var pageSize = globalFilter.getPageSize();
        var pageable = pageNumber != null && pageSize != null ? PageableUtils.from(pageNumber, pageSize) : null;

        return new FetchChartDataQuery(chart, filters, startDate, endDate, tenantId, pageable);
    }

    private List<QueryFilter> formatLabelsFilters(List<QueryFilter> filters) {
        return Optional.ofNullable(filters)
            .map(queryFilters -> queryFilters.stream().map(f ->
            {
                if (f.field() == QueryFilter.Field.LABELS && f.value() instanceof String filterStr) {
                    return QueryFilter.builder()
                        .field(f.field())
                        .operation(f.operation())
                        .value(Label.from(filterStr))
                        .build();
                }
                return f;
            }).toList())
            .orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "charts/preview")
    @Operation(tags = { "Dashboards" }, summary = "Preview a chart data")
    public PagedResults<Map<String, Object>> previewChart(
        @Parameter(description = "The chart") @Body @Valid PreviewRequest previewRequest) throws IOException {
        var fetchChartDataQuery = buildChartPreviewDataQuery(previewRequest);
        return fetchChartData(fetchChartDataQuery);
    }

    private FetchChartDataQuery buildChartPreviewDataQuery(PreviewRequest previewRequest) {
        String tenantId = tenantService.resolveTenant();
        Chart<?> chart = YamlParser.parse(previewRequest.chart(), Chart.class);
        modelValidator.validate(chart);
        ChartFiltersOverrides globalFilter = previewRequest.globalFilter();

        List<QueryFilter> filters = globalFilter != null ? globalFilter.getFilters() : null;

        filters = formatLabelsFilters(filters);

        ZonedDateTime endDate = null;
        ZonedDateTime startDate;
        if (filters != null) {
            TimeLineSearch timeLineSearch = TimeLineSearch.extractFrom(filters);
            validateTimeline(timeLineSearch.getStartDate(), timeLineSearch.getEndDate());

            endDate = timeLineSearch.getEndDate();
            startDate = timeLineSearch.getStartDate();
        } else {
            startDate = ZonedDateTime.now().minusDays(8);
        }

        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("`endDate` must be after `startDate`.");
        }
        Pageable pageable = null;
        if (globalFilter != null && globalFilter.getPageSize() != null && globalFilter.getPageNumber() != null) {
            pageable = PageableUtils.from(globalFilter.getPageNumber(), globalFilter.getPageSize());
        }

        return new FetchChartDataQuery(chart, filters, startDate, endDate, tenantId, pageable);
    }

    protected record FetchChartDataQuery(Chart<?> chart, List<QueryFilter> filters, ZonedDateTime startDate,
        ZonedDateTime endDate, String tenantId, Pageable pageable) {
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected PagedResults<Map<String, Object>> fetchChartData(FetchChartDataQuery fetchChartDataQuery) throws IOException {
        var chart = fetchChartDataQuery.chart();
        var filters = fetchChartDataQuery.filters();
        var startDate = fetchChartDataQuery.startDate();
        var endDate = fetchChartDataQuery.endDate();
        var tenantId = fetchChartDataQuery.tenantId();
        var pageable = fetchChartDataQuery.pageable();

        if (chart instanceof DataChart dataChart) {
            DataFilter<?, ?> dataChartDatas = dataChart.getData();
            dataChartDatas.updateWhereWithGlobalFilters(filters, startDate, endDate);

            // StartDate & EndDate are only set in the globalFilter for JDBC
            // TODO: Check if we can remove them from generate() for ElasticSearch as they are already set in the where property
            return PagedResults.of(this.chartDataService.generate(tenantId, dataChart, startDate, endDate, pageable));
        } else if (chart instanceof DataChartKPI dataChartKPI) {
            DataFilterKPI<?, ?> dataChartDatas = dataChartKPI.getData();
            dataChartDatas.updateWhereWithGlobalFilters(filters, startDate, endDate);

            return PagedResults.of(new ArrayListTotal<>(this.chartDataService.generateKPI(tenantId, dataChartKPI, startDate, endDate), 1));
        } else if (chart instanceof Markdown markdownChart) {
            if (markdownChart.getSource() != null && markdownChart.getSource() instanceof FlowDescription flowDescription) {
                Optional<Flow> optionalFlow = flowRepository.findById(tenantId, flowDescription.getNamespace(), flowDescription.getFlowId());
                if (optionalFlow.isPresent()) {
                    Flow flow = optionalFlow.get();
                    Map<String, Object> descriptionMap = Map.of(
                        "description", flow.getDescription() != null ? flow.getDescription() : ""
                    );

                    return PagedResults.of(new ArrayListTotal<>(List.of(descriptionMap), 1));
                } else {
                    throw new IllegalArgumentException("Flow not found");
                }
            }
        }

        throw new IllegalArgumentException("Only data charts can be generated.");
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{id}/charts/{chartId}/export", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(tags = { "Dashboards" }, summary = "Export a dashboard chart data")
    public HttpResponse<byte[]> exportDashboardChart(
        @Parameter(description = "The dashboard id") @PathVariable String id,
        @Parameter(description = "The chart id") @PathVariable String chartId,
        @Parameter(description = "The export format") @QueryValue(defaultValue = "CSV") ExportFormat format,
        @RequestBody(description = "The filters to apply, some can override chart definition like labels & namespace") @Body ChartFiltersOverrides globalFilter) throws IOException {
        var fetchChartDataQuery = buildDashboardChardDataQuery(id, chartId, globalFilter);
        if (fetchChartDataQuery == null) {
            return null;
        }
        assertExportable(fetchChartDataQuery.chart());
        var fetchedData = fetchChartData(fetchChartDataQuery);

        return export(fetchChartDataQuery.chart(), fetchedData.getResults(), "%s_%s_export".formatted(id, chartId), format);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "charts/export", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(tags = { "Dashboards" }, summary = "Export a chart data")
    public HttpResponse<byte[]> exportChart(
        @Parameter(description = "The export format") @QueryValue(defaultValue = "CSV") ExportFormat format,
        @Parameter(description = "The chart") @Body @Valid PreviewRequest previewRequest) throws IOException {
        var fetchChartDataQuery = buildChartPreviewDataQuery(previewRequest);
        assertExportable(fetchChartDataQuery.chart());
        var fetchedData = fetchChartData(fetchChartDataQuery);

        return export(fetchChartDataQuery.chart(), fetchedData.getResults(), "%s_%s_export".formatted("default-dashboard", fetchChartDataQuery.chart().getId()), format);
    }

    private void assertExportable(Chart<?> chart) {
        if (!(chart instanceof DataChart) && !(chart instanceof DataChartKPI)) {
            throw new IllegalArgumentException("Only data charts can be exported.");
        }
    }

    private HttpResponse<byte[]> export(Chart<?> chart, List<Map<String, Object>> rows, String filename, ExportFormat format) throws IOException {
        var byteArrayOutputStream = new ByteArrayOutputStream();

        if (format == ExportFormat.ION) {
            FileSerde.writeAll(byteArrayOutputStream, Flux.fromIterable(rows)).block();
        } else {
            var outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8);
            if (rows.isEmpty() && chart instanceof DataChart<?, ?> dataChart) {
                CSVUtils.toCSV(outputStreamWriter, rows, List.copyOf(dataChart.getData().getColumns().keySet()));
            } else {
                CSVUtils.toCSV(outputStreamWriter, rows);
            }
        }

        var fullFilename = "%s.%s".formatted(filename, format.name().toLowerCase());
        return HttpResponse.ok(byteArrayOutputStream.toByteArray()).header("Content-Disposition", "attachment; filename=\"%s\"".formatted(fullFilename));
    }

    public record PreviewRequest(
        @Parameter(description = "The chart") @NotBlank String chart,
        @Parameter(description = "The filters to apply, some can override chart definition like labels & namespace") @Nullable ChartFiltersOverrides globalFilter) {
    }

    @Getter
    public static class DashboardResponse {
        @TenantId
        private final String tenantId;
        @NotNull
        @NotBlank
        private final String id;
        @NotNull
        @NotBlank
        private final String title;
        private final String description;
        private final TimeWindow timeWindow;
        private final List<Chart<?>> charts;
        @NotNull
        private final boolean deleted;
        private final Instant created;
        private final Instant updated;
        private final String sourceCode;

        public DashboardResponse(Dashboard dashboard) {
            this.tenantId = dashboard.getTenantId();
            this.id = dashboard.getId();
            this.title = dashboard.getTitle();
            this.description = dashboard.getDescription();
            this.timeWindow = dashboard.getTimeWindow();
            this.charts = dashboard.getCharts();
            this.deleted = dashboard.isDeleted();
            this.created = dashboard.getCreated();
            this.updated = dashboard.getUpdated();
            this.sourceCode = dashboard.getSourceCode();
        }

        @JsonCreator
        public DashboardResponse(String tenantId, String id, String title, String description, TimeWindow timeWindow, List<Chart<?>> charts, boolean deleted, Instant created, Instant updated,
            String sourceCode) {
            this.tenantId = tenantId;
            this.id = id;
            this.title = title;
            this.description = description;
            this.timeWindow = timeWindow;
            this.charts = charts;
            this.deleted = deleted;
            this.created = created;
            this.updated = updated;
            this.sourceCode = sourceCode;
        }
    }
}
