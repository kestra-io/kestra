package io.kestra.webserver.controllers.api;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
import io.kestra.webserver.utils.RequestUtils;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.format.Format;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;

import java.time.ZonedDateTime;
import java.util.List;

@Controller("/api/v1/taskruns")
@Requires(property = "kestra.repository.type", value = "elasticsearch")
public class TaskRunController {
    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search")
    @Operation(tags = {"Executions"}, summary = "Search for taskruns")
    public PagedResults<TaskRun> findTaskRun(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate,
        @Parameter(description = "A state filter") @Nullable @QueryValue List<State.Type> state,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue List<String> labels,
        @Parameter(description = "The trigger execution id") @Nullable @QueryValue String triggerExecutionId,
        @Parameter(description = "A execution child filter") @Nullable @QueryValue ExecutionRepositoryInterface.ChildFilter childFilter
    ) {
        return PagedResults.of(executionRepository.findTaskRun(
            PageableUtils.from(page, size, sort, executionRepository.sortMapping()),
            query,
            tenantService.resolveTenant(),
            namespace,
            flowId,
            startDate,
            endDate,
            state,
            RequestUtils.toMap(labels),
            triggerExecutionId,
            childFilter
        ));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/maxTaskRunSetting")
    @Hidden
    public Integer maxTaskRunSetting() {
        return executionRepository.maxTaskRunSetting();
    }
}
