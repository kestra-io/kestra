package io.kestra.webserver.controllers.api;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.core.models.dashboards.charts.DataChartKPI;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.DashboardRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.tenant.TenantService;
import io.kestra.plugin.core.dashboard.chart.Markdown;
import io.kestra.plugin.core.dashboard.chart.Table;
import io.kestra.plugin.core.dashboard.chart.mardown.sources.FlowDescription;
import io.kestra.webserver.models.ChartFiltersOverrides;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.CSVUtils;
import io.kestra.webserver.utils.PageableUtils;
import io.kestra.webserver.utils.TimeLineSearch;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Pattern;

import static io.kestra.core.utils.DateUtils.validateTimeline;

@Validated
@Controller("/api/v1/{tenant}/dashboards")
@Slf4j
public class DashboardController {
    protected static final YamlParser YAML_PARSER = new YamlParser();
    public static final Pattern DASHBOARD_ID_PATTERN = Pattern.compile("^id:.*$", Pattern.MULTILINE);

    @Inject
    private DashboardRepositoryInterface dashboardRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    protected TenantService tenantService;

    @Inject
    protected ModelValidator modelValidator;

    @ExecuteOn(TaskExecutors.IO)
    @Get
    @Operation(tags = {"Dashboards"}, summary = "Search for dashboards")
    public PagedResults<Dashboard> searchDashboards(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(description = "The filter query") @Nullable @QueryValue String q,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort
    ) throws ConstraintViolationException {
        return PagedResults.of(dashboardRepository.list(PageableUtils.from(page, size, sort), tenantService.resolveTenant(), q));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{id}")
    @Operation(tags = {"Dashboards"}, summary = "Get a dashboard")
    public Dashboard getDashboard(
        @Parameter(description = "The dashboard id") @PathVariable String id
    ) throws ConstraintViolationException {
        return dashboardRepository.get(tenantService.resolveTenant(), id).map(d -> {
            if (!DASHBOARD_ID_PATTERN.matcher(d.getSourceCode()).find()) {
                return d.toBuilder().sourceCode("id: " + d.getId() + "\n" + d.getSourceCode()).build();
            }
            return d;
        }).orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(consumes = MediaType.APPLICATION_YAML)
    @Operation(tags = {"Dashboards"}, summary = "Create a dashboard from yaml source")
    public HttpResponse<Dashboard> createDashboard(
        @RequestBody(description = "The dashboard definition as YAML") @Body String dashboard
    ) throws ConstraintViolationException {
        Dashboard dashboardParsed = parseDashboard(dashboard);

        if (dashboardParsed.getId() == null) {
            throw new IllegalArgumentException("Dashboard id is mandatory");
        }
        modelValidator.validate(dashboardParsed);

        Optional<Dashboard> existingDashboard = dashboardRepository.get(tenantService.resolveTenant(), dashboardParsed.getId());
        if (existingDashboard.isPresent()) {
            throw new ConstraintViolationException(Collections.singleton(ManualConstraintViolation.of(
                "Dashboard id already exists",
                dashboardParsed,
                Dashboard.class,
                "dashboard.id",
                dashboardParsed.getId()
            )));
        }

        return HttpResponse.ok(this.save(null, dashboardParsed, dashboard));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "validate", consumes = MediaType.APPLICATION_YAML)
    @Operation(tags = {"Dashboards"}, summary = "Validate dashboard from yaml source")
    public ValidateConstraintViolation validateDashboard(
        @RequestBody(description = "The dashboard definition as YAML") @Body String dashboard
    ) throws ConstraintViolationException {
        ValidateConstraintViolation.ValidateConstraintViolationBuilder<?, ?> validateConstraintViolationBuilder = ValidateConstraintViolation.builder();
        validateConstraintViolationBuilder.index(0);

        try {
            Dashboard parsed = YamlParser.parse(dashboard, Dashboard.class).toBuilder().deleted(false).build();

            modelValidator.validate(parsed);
        } catch (ConstraintViolationException e) {
            validateConstraintViolationBuilder.constraints(e.getMessage());
        } catch (RuntimeException re) {
            // In case of any error, we add a validation violation so the error is displayed in the UI.
            // We may change that by throwing an internal error and handle it in the UI, but this should not occur except for rare cases
            // in dev like incompatible plugin versions.
            log.error("Unable to validate the dashboard", re);
            validateConstraintViolationBuilder.constraints("Unable to validate the dashboard: " + re.getMessage());
        }

        return validateConstraintViolationBuilder.build();
    }

    @Put(uri = "{id}", consumes = MediaType.APPLICATION_YAML)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Dashboards"}, summary = "Update a dashboard")
    public HttpResponse<Dashboard> updateDashboard(
        @Parameter(description = "The dashboard id") @PathVariable String id,
        @RequestBody(description = "The dashboard definition as YAML") @Body String dashboard
    ) throws ConstraintViolationException {
        Optional<Dashboard> existingDashboard = dashboardRepository.get(tenantService.resolveTenant(), id);
        if (existingDashboard.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
        Dashboard dashboardToSave = parseDashboard(dashboard);
        if (!dashboardToSave.getId().equals(id)) {
            throw new ConstraintViolationException(Set.of(ManualConstraintViolation.of(
                "Illegal dashboard id update",
                dashboardToSave,
                Dashboard.class,
                "dashboard.id",
                dashboardToSave.getId()
            )));
        }
        modelValidator.validate(dashboardToSave);

        return HttpResponse.ok(this.save(existingDashboard.get(), dashboardToSave, dashboard));
    }

    private Dashboard parseDashboard(String dashboard) {
        return YamlParser.parse(dashboard, Dashboard.class).toBuilder()
            .tenantId(tenantService.resolveTenant())
            .deleted(false).build();
    }

    protected Dashboard save(Dashboard previousDashboard, Dashboard dashboard, String source) {
        return dashboardRepository.save(previousDashboard, dashboard, source);
    }

    @Delete(uri = "{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Dashboards"}, summary = "Delete a dashboard")
    public HttpResponse<Void> deleteDashboard(
        @Parameter(description = "The dashboard id") @PathVariable String id
    ) throws ConstraintViolationException {
        if (dashboardRepository.delete(tenantService.resolveTenant(), id) != null) {
            return HttpResponse.status(HttpStatus.NO_CONTENT);
        } else {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{id}/charts/{chartId}")
    @Operation(tags = {"Dashboards"}, summary = "Generate a dashboard chart data")
    public PagedResults<Map<String, Object>> getDashboardChartData(
        @Parameter(description = "The dashboard id") @PathVariable String id,
        @Parameter(description = "The chart id") @PathVariable String chartId,
        @RequestBody(description = "The filters to apply, some can override chart definition like labels & namespace") @Body ChartFiltersOverrides globalFilter
    ) throws IOException {
        var fetchChartDataQuery = buildDashboardChardDataQuery(id, chartId, globalFilter);

        if (fetchChartDataQuery == null) return null;

        return fetchChartData(fetchChartDataQuery);
    }

    private FetchChartDataQuery buildDashboardChardDataQuery(String id, String chartId, ChartFiltersOverrides globalFilter) {
        String tenantId = tenantService.resolveTenant();
        List<QueryFilter> filters = globalFilter.getFilters();
        Dashboard dashboard = dashboardRepository.get(tenantId, id).orElse(null);
        if (dashboard == null) {
            return null;
        }

        TimeLineSearch timeLineSearch = TimeLineSearch.extractFrom(filters);
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

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "charts/preview")
    @Operation(tags = {"Dashboards"}, summary = "Preview a chart data")
    public PagedResults<Map<String, Object>> previewChart(
        @Parameter(description = "The chart") @Body @Valid PreviewRequest previewRequest
    ) throws IOException {
        var fetchChartDataQuery = buildChartPreviewDataQuery(previewRequest);
        return fetchChartData(fetchChartDataQuery);
    }

    private FetchChartDataQuery buildChartPreviewDataQuery(PreviewRequest previewRequest){
        String tenantId = tenantService.resolveTenant();
        Chart<?> chart = YAML_PARSER.parse(previewRequest.chart(), Chart.class);
        ChartFiltersOverrides globalFilter = previewRequest.globalFilter();

        List<QueryFilter> filters =
            globalFilter != null ? globalFilter.getFilters() : null;

        ZonedDateTime endDate = null;
        ZonedDateTime startDate = null;
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

    private record FetchChartDataQuery(Chart<?> chart, List<QueryFilter> filters, ZonedDateTime startDate,
                                       ZonedDateTime endDate, String tenantId, Pageable pageable) {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private PagedResults<Map<String, Object>> fetchChartData(FetchChartDataQuery fetchChartDataQuery) throws IOException {
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
            return PagedResults.of(this.dashboardRepository.generate(tenantId, dataChart, startDate, endDate, pageable));
        } else if (chart instanceof DataChartKPI dataChartKPI) {
            DataFilterKPI<?, ?> dataChartDatas = dataChartKPI.getData();
            dataChartDatas.updateWhereWithGlobalFilters(filters, startDate, endDate);

            return PagedResults.of(new ArrayListTotal<>(this.dashboardRepository.generateKPI(tenantId, dataChartKPI, startDate, endDate), 1));
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
    @Post(uri = "validate/chart", consumes = MediaType.APPLICATION_YAML)
    @Operation(tags = {"Dashboards"}, summary = "Validate a chart from yaml source")
    public ValidateConstraintViolation validateChart(
        @RequestBody(description = "The chart definition as YAML") @Body String chart
    ) throws ConstraintViolationException {
        ValidateConstraintViolation.ValidateConstraintViolationBuilder<?, ?> validateConstraintViolationBuilder = ValidateConstraintViolation.builder();
            validateConstraintViolationBuilder.index(0);

        try {
            Chart<?> parsed = YamlParser.parse(chart, Chart.class);

            modelValidator.validate(parsed);
        } catch (ConstraintViolationException e) {
            validateConstraintViolationBuilder.constraints(e.getMessage());
        } catch (RuntimeException re) {
            // In case of any error, we add a validation violation so the error is displayed in the UI.
            // We may change that by throwing an internal error and handle it in the UI, but this should not occur except for rare cases
            // in dev like incompatible plugin versions.
            log.error("Unable to validate the dashboard", re);
            validateConstraintViolationBuilder.constraints("Unable to validate the chart: " + re.getMessage());
        }

        return validateConstraintViolationBuilder.build();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{id}/charts/{chartId}/export/to-csv", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(tags = {"Dashboards"}, summary = "Export a dashboard chart data to CSV")
    public HttpResponse<byte[]> exportDashboardChartDataToCSV(
        @Parameter(description = "The dashboard id") @PathVariable String id,
        @Parameter(description = "The chart id") @PathVariable String chartId,
        @RequestBody(description = "The filters to apply, some can override chart definition like labels & namespace") @Body ChartFiltersOverrides globalFilter
    ) throws IOException {
        var fetchChartDataQuery = buildDashboardChardDataQuery(id, chartId, globalFilter);
        if (fetchChartDataQuery == null) {
            return null;
        }
        if (!(fetchChartDataQuery.chart instanceof Table)) {
            throw new IllegalArgumentException("Only Table data charts can be exported.");
        }
        var fetchedData = fetchChartData(fetchChartDataQuery);

        var byteArrayOutputStream = new ByteArrayOutputStream();
        var outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream);
        CSVUtils.toCSV(outputStreamWriter, fetchedData.getResults());

        var filename = "%s_%s_export.csv".formatted(id, chartId);
        return HttpResponse.ok(byteArrayOutputStream.toByteArray()).header("Content-Disposition", "attachment; filename=\"%s\"".formatted(filename));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "charts/export/to-csv", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(tags = {"Dashboards"}, summary = "Export a table chart data to CSV")
    public HttpResponse<byte[]> exportChartToCsv(
        @Parameter(description = "The chart") @Body @Valid PreviewRequest previewRequest
    ) throws IOException {
        var fetchChartDataQuery = buildChartPreviewDataQuery(previewRequest);
        if (!(fetchChartDataQuery.chart instanceof Table)) {
            throw new IllegalArgumentException("Only Table data charts can be exported.");
        }
        var fetchedData = fetchChartData(fetchChartDataQuery);

        var byteArrayOutputStream = new ByteArrayOutputStream();
        var outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream);
        CSVUtils.toCSV(outputStreamWriter, fetchedData.getResults());

        var filename = "%s_%s_export.csv".formatted("default-dashboard", fetchChartDataQuery.chart().getId());
        return HttpResponse.ok(byteArrayOutputStream.toByteArray()).header("Content-Disposition", "attachment; filename=\"%s\"".formatted(filename));
    }

    @Introspected
    public record PreviewRequest(
        @Parameter(description = "The chart") @NotBlank String chart,
        @Parameter(description = "The filters to apply, some can override chart definition like labels & namespace") @Nullable ChartFiltersOverrides globalFilter) {}

}
