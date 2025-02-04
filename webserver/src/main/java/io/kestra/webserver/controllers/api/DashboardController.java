package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.dashboards.GlobalFilter;
import io.kestra.core.models.dashboards.charts.Chart;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.repositories.DashboardRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
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
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Validated
@Controller("/api/v1/dashboards")
@Slf4j
public class DashboardController {
    protected static final YamlParser YAML_PARSER = new YamlParser();

    @Inject
    private DashboardRepositoryInterface dashboardRepository;

    @Inject
    protected TenantService tenantService;

    @Inject
    protected ModelValidator modelValidator;

    @ExecuteOn(TaskExecutors.IO)
    @Get
    @Operation(tags = {"Dashboards"}, summary = "List all dashboards")
    public PagedResults<Dashboard> list(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(description = "The filter query") @Nullable @QueryValue String q,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort
    ) throws ConstraintViolationException, IllegalVariableEvaluationException {
        return PagedResults.of(dashboardRepository.list(PageableUtils.from(page, size, sort), tenantService.resolveTenant(), q));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{id}")
    @Operation(tags = {"Dashboards"}, summary = "Retrieve a dashboard")
    public Dashboard get(
        @Parameter(description = "The dashboard id") @PathVariable String id
    ) throws ConstraintViolationException, IllegalVariableEvaluationException {
        return dashboardRepository.get(tenantService.resolveTenant(), id).orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(consumes = MediaType.APPLICATION_YAML)
    @Operation(tags = {"Dashboards"}, summary = "Create a dashboard from yaml source")
    public HttpResponse<Dashboard> create(
        @Parameter(description = "The dashboard") @Body String dashboard
    ) throws ConstraintViolationException, JsonProcessingException {
        Dashboard dashboardParsed = YAML_PARSER.parse(dashboard, Dashboard.class).toBuilder().deleted(false).build();
        modelValidator.validate(dashboardParsed);

        if (dashboardParsed.getId() != null) {
            throw new IllegalArgumentException("Dashboard id is not editable");
        }

        return HttpResponse.ok(this.save(null, dashboardParsed.toBuilder().id(IdUtils.create()).build(), dashboard));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "validate", consumes = MediaType.APPLICATION_YAML)
    @Operation(tags = {"Dashboards"}, summary = "Validate dashboard from yaml source")
    public ValidateConstraintViolation validate(
        @Parameter(description = "The dashboard") @Body String dashboard
    ) throws ConstraintViolationException, JsonProcessingException {
        ValidateConstraintViolation.ValidateConstraintViolationBuilder<?, ?> validateConstraintViolationBuilder = ValidateConstraintViolation.builder();
        validateConstraintViolationBuilder.index(0);

        try {
            Dashboard parsed = YAML_PARSER.parse(dashboard, Dashboard.class).toBuilder().deleted(false).build();

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
    public HttpResponse<Dashboard> update(
        @Parameter(description = "The dashboard id") @PathVariable String id,
        @Parameter(description = "The dashboard") @Body String dashboard
    ) throws ConstraintViolationException, JsonProcessingException {
        Optional<Dashboard> existingDashboard = dashboardRepository.get(tenantService.resolveTenant(), id);
        if (existingDashboard.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
        Dashboard dashboardToSave = JacksonMapper.ofYaml().readValue(dashboard, Dashboard.class).toBuilder().deleted(false).build();
        modelValidator.validate(dashboardToSave);

        return HttpResponse.ok(this.save(existingDashboard.get(), dashboardToSave, dashboard));
    }

    protected Dashboard save(Dashboard previousDashboard, Dashboard dashboard, String source) {
        return dashboardRepository.save(previousDashboard, dashboard, source);
    }

    @Delete(uri = "{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Dashboards"}, summary = "Delete a dashboard")
    public HttpResponse<Void> delete(
        @Parameter(description = "The dashboard id") @PathVariable String id
    ) throws ConstraintViolationException, JsonProcessingException {
        if (dashboardRepository.delete(tenantService.resolveTenant(), id) != null) {
            return HttpResponse.status(HttpStatus.NO_CONTENT);
        } else {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{id}/charts/{chartId}")
    @Operation(tags = {"Dashboards"}, summary = "Generate a dashboard chart data")
    public PagedResults<Map<String, Object>> dashboardChart(
        @Parameter(description = "The dashboard id") @PathVariable String id,
        @Parameter(description = "The chart id") @PathVariable String chartId,
        @Parameter(description = "The filters to apply, some can override chart definition like labels & namespace") @Body GlobalFilter globalFilter
    ) throws IOException {
        String tenantId = tenantService.resolveTenant();
        Dashboard dashboard = dashboardRepository.get(tenantId, id).orElse(null);
        if (dashboard == null) {
            return null;
        }

        ZonedDateTime endDate = globalFilter.getEndDate();
        ZonedDateTime startDate = globalFilter.getStartDate();
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

        if (chart instanceof DataChart dataChart) {
            Integer pageNumber = globalFilter.getPageNumber();
            Integer pageSize = globalFilter.getPageSize();

            dataChart.getData().setGlobalFilter(globalFilter);

            // StartDate & EndDate are only set in the globalFilter for JDBC
            // TODO: Check if we can remove them from generate() for ElasticSearch as they are already set in the where property
            return PagedResults.of(this.dashboardRepository.generate(tenantId, dataChart, startDate, endDate, pageNumber != null && pageSize != null ? PageableUtils.from(pageNumber, pageSize) : null));
        }

        throw new IllegalArgumentException("Only data charts can be generated.");
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "charts/preview", consumes = MediaType.APPLICATION_YAML)
    @Operation(tags = {"Dashboards"}, summary = "Preview a chart data")
    public PagedResults<Map<String, Object>> previewChart(
        @Parameter(description = "The chart") @Body String chart
    ) throws IOException {
        Chart<?> parsed = YAML_PARSER.parse(chart, Chart.class);

        return PagedResults.of(this.dashboardRepository.generate(tenantService.resolveTenant(), (DataChart) parsed, ZonedDateTime.now().minusDays(8), ZonedDateTime.now(), null));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "validate/chart", consumes = MediaType.APPLICATION_YAML)
    @Operation(tags = {"Dashboards"}, summary = "Validate a chart from yaml source")
    public ValidateConstraintViolation validateChart(
        @Parameter(description = "The dashboard") @Body String chart
    ) throws ConstraintViolationException {
        ValidateConstraintViolation.ValidateConstraintViolationBuilder<?, ?> validateConstraintViolationBuilder = ValidateConstraintViolation.builder();
            validateConstraintViolationBuilder.index(0);

        try {
            Chart<?> parsed = YAML_PARSER.parse(chart, Chart.class);

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


}
