package io.kestra.webserver.controllers.api;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
import io.kestra.webserver.utils.QueryFilterUtils;
import io.kestra.webserver.utils.RequestUtils;
import io.kestra.webserver.utils.TimeLineSearch;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.format.Format;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Min;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static io.kestra.core.utils.DateUtils.validateTimeline;

@Controller("/api/v1/taskruns")
@Requires(property = "kestra.repository.type", value = "elasticsearch")
public class TaskRunController {
    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search")
    @Operation(tags = {"Executions"}, summary = "Search for taskruns, only available with the Elasticsearch repository")
    public PagedResults<TaskRun> searchTaskRun(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "Filters") @QueryFilterFormat List<QueryFilter> filters,
        // Deprecated params
        @Parameter(description = "A string filter",deprecated = true) @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix", deprecated = true) @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id filter",deprecated = true) @Nullable @QueryValue String flowId,
        @Parameter(description = "The start datetime",deprecated = true) @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Parameter(description = "The end datetime", deprecated = true) @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Parameter(description = "A time range filter relative to the current time", deprecated = true, examples = {
            @ExampleObject(name = "Filter last 5 minutes", value = "PT5M"),
            @ExampleObject(name = "Filter last 24 hours", value = "P1D")
        }) @Nullable @QueryValue Duration timeRange,
        @Parameter(description = "A state filter",deprecated = true) @Nullable @QueryValue List<State.Type> state,
        @Parameter(description = "A labels filter as a list of 'key:value'", deprecated = true) @Nullable @QueryValue @Format("MULTI") List<String> labels,
        @Parameter(description = "The trigger execution id",deprecated = true) @Nullable @QueryValue String triggerExecutionId,
        @Parameter(description = "A execution child filter", deprecated = true) @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter

    ) throws HttpStatusException {

        // If filters is empty, map old params to QueryFilter
        if (filters == null || filters.isEmpty()) {
            filters = RequestUtils.mapLegacyParamsToFilters(
                query,
                namespace,
                flowId,
                triggerExecutionId,
                null,
                startDate,
                endDate,
                null,
                labels,
                timeRange,
                childFilter,
                state,
                null);
        }
        final ZonedDateTime now = ZonedDateTime.now();

        TimeLineSearch timeLineSearch = TimeLineSearch.extractFrom(filters);
        validateTimeline(timeLineSearch.getStartDate(), timeLineSearch.getEndDate());

        ZonedDateTime resolvedStartDate = timeLineSearch.getStartDate();

        // Update filters with the resolved startDate
        filters = QueryFilterUtils.updateFilters(filters, resolvedStartDate);

        return PagedResults.of(executionRepository.findTaskRun(
            PageableUtils.from(page, size, sort),
            tenantService.resolveTenant(),
            filters
        ));
    }

}
