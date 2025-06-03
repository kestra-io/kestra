package io.kestra.webserver.controllers.api;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.core.models.dashboards.charts.DataChartKPI;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.DashboardRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.dashboard.chart.Markdown;
import io.kestra.plugin.core.dashboard.chart.mardown.sources.FlowDescription;
import io.kestra.webserver.models.GlobalFilter;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
import io.kestra.webserver.utils.TimeLineSearch;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
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
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.kestra.core.utils.DateUtils.validateTimeline;

@Validated
@Controller("/api/v1/main/dashboards")
@Slf4j
public class DashboardController {
    protected static final YamlParser YAML_PARSER = new YamlParser();

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
        return dashboardRepository.get(tenantService.resolveTenant(), id).orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(consumes = MediaType.APPLICATION_YAML)
    @Operation(tags = {"Dashboards"}, summary = "Create a dashboard from yaml source")
    public HttpResponse<Dashboard> createDashboard(
        @RequestBody(description = "The dashboard definition as YAML") @Body String dashboard
    ) throws ConstraintViolationException {
        Dashboard dashboardParsed = parseDashboard(dashboard);
        modelValidator.validate(dashboardParsed);

        if (dashboardParsed.getId() != null) {
            throw new IllegalArgumentException("Dashboard id is not editable");
        }

        return HttpResponse.ok(this.save(null, dashboardParsed.toBuilder().id(IdUtils.create()).build(), dashboard));
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
    @SuppressWarnings({"rawtypes", "unchecked"})
    public PagedResults<Map<String, Object>> getDashboardChartData(
        @Parameter(description = "The dashboard id") @PathVariable String id,
        @Parameter(description = "The chart id") @PathVariable String chartId,
        @RequestBody(description = "The filters to apply, some can override chart definition like labels & namespace") @Body GlobalFilter globalFilter
    ) throws IOException {
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
        if (startDate == null || endDate == null) {
            endDate = ZonedDateTime.now();
            startDate = endDate.minus(dashboard.getTimeWindow().getDefaultDuration());
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("`endDate` must be after `startDate`.");
        }

        Duration windowDuration = Duration.ofSeconds(endDate.minus(Duration.ofSeconds(startDate.toEpochSecond())).toEpochSecond());
        if (windowDuration.compareTo(dashboard.getTimeWindow().getMax()) > 0) {
            throw new IllegalArgumentException("The queried window is larger than the max allowed one.");
        }

        Chart<?> chart = dashboard.getCharts().stream().filter(g -> g.getId().equals(chartId)).findFirst().orElse(null);
        if (chart == null) {
            return null;
        }

        Integer pageNumber = globalFilter.getPageNumber();
        Integer pageSize = globalFilter.getPageSize();

        if (chart instanceof DataChart dataChart) {
            DataFilter<?, ?> dataChartDatas = dataChart.getData();
            dataChartDatas.updateWhereWithGlobalFilters(filters, startDate, endDate);

            // StartDate & EndDate are only set in the globalFilter for JDBC
            // TODO: Check if we can remove them from generate() for ElasticSearch as they are already set in the where property
            return PagedResults.of(this.dashboardRepository.generate(tenantId, dataChart, startDate, endDate, pageNumber != null && pageSize != null ? PageableUtils.from(pageNumber, pageSize) : null));
        } else if (chart instanceof DataChartKPI dataChartKPI) {
            DataFilterKPI<?, ?> dataChartDatas = dataChartKPI.getData();
            dataChartDatas.updateWhereWithGlobalFilters(filters, startDate, endDate);

            return PagedResults.of(new ArrayListTotal<>(this.dashboardRepository.generateKPI(tenantId, dataChartKPI, startDate, endDate), 1));
        } else if (chart instanceof Markdown markdownChart) {
            if (markdownChart.getSource() != null && markdownChart.getSource() instanceof FlowDescription flowDescription) {
                Optional<Flow> optionalFlow = flowRepository.findById(this.tenantService.resolveTenant(), flowDescription.getNamespace(), flowDescription.getFlowId());
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
    @Post(uri = "charts/preview")
    @Operation(tags = {"Dashboards"}, summary = "Preview a chart data")
    @SuppressWarnings({"rawtypes", "unchecked"})
    public PagedResults<Map<String, Object>> previewChart(
        @Parameter(description = "The chart") @Body @Valid PreviewRequest previewRequest
    ) throws IOException {
        Chart<?> chart = YAML_PARSER.parse(previewRequest.chart(), Chart.class);
        GlobalFilter globalFilter = previewRequest.globalFilter();

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

        if (chart instanceof DataChart dataChart) {
            DataFilter<?, ?> dataChartDatas = dataChart.getData();
            dataChartDatas.updateWhereWithGlobalFilters(filters, startDate, endDate);

            // StartDate & EndDate are only set in the globalFilter for JDBC
            // TODO: Check if we can remove them from generate() for ElasticSearch as they are already set in the where property
            return PagedResults.of(this.dashboardRepository.generate(this.tenantService.resolveTenant(), dataChart, startDate, endDate, null));
        } else if (chart instanceof DataChartKPI dataChartKPI) {
            DataFilterKPI<?, ?> dataChartDatas = dataChartKPI.getData();
            dataChartDatas.updateWhereWithGlobalFilters(filters, startDate, endDate);

            return PagedResults.of(new ArrayListTotal<>(this.dashboardRepository.generateKPI(this.tenantService.resolveTenant(), dataChartKPI, startDate, endDate),1));
        } else if (chart instanceof Markdown markdownChart) {
            if (markdownChart.getSource() != null && markdownChart.getSource() instanceof FlowDescription flowDescription) {
                Optional<Flow> optionalFlow = flowRepository.findById(this.tenantService.resolveTenant(), flowDescription.getNamespace(), flowDescription.getFlowId());
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

        throw new IllegalArgumentException("Chart is not an instance of DataChart.");
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

    @Introspected
    public record PreviewRequest(
        @Parameter(description = "The chart") String chart,
        @Parameter(description = "The filters to apply, some can override chart definition like labels & namespace") @Nullable GlobalFilter globalFilter) {}

}
