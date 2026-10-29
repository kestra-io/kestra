package io.kestra.webserver.controllers.api;

import java.util.Map;
import java.util.Objects;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.models.api.ApiLightExecution;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.services.playground.PlaygroundHeartbeats;
import io.kestra.webserver.services.playground.PlaygroundPlan;
import io.kestra.webserver.services.playground.PlaygroundRunMode;
import io.kestra.webserver.services.playground.PlaygroundService;
import io.kestra.webserver.services.playground.PlaygroundService.ReusedTaskRun;
import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Validated
@Controller("/api/v1/{tenant}/flows/{namespace}/{id}/playground")
public class PlaygroundController {
    private static final String CLIENT_ID_PATTERN = "^[a-zA-Z0-9_-]{1,64}$";

    protected final PlaygroundService playgroundService;
    protected final PlaygroundHeartbeats playgroundHeartbeats;
    protected final TenantService tenantService;

    @Inject
    public PlaygroundController(PlaygroundService playgroundService, PlaygroundHeartbeats playgroundHeartbeats, TenantService tenantService) {
        this.playgroundService = Objects.requireNonNull(playgroundService);
        this.playgroundHeartbeats = Objects.requireNonNull(playgroundHeartbeats);
        this.tenantService = Objects.requireNonNull(tenantService);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/runs")
    @Operation(tags = { "Playground" }, summary = "List the caller's Playground runs of a flow, newest first")
    public PagedResults<ApiLightExecution> listPlaygroundRuns(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) @Max(PageableUtils.MAX_PAGE_SIZE) int size) {
        ArrayListTotal<Execution> runs = playgroundService.runs(tenantService.resolveTenant(), namespace, id, page, size);
        return PagedResults.of(new ArrayListTotal<>(runs.stream().map(ApiLightExecution::of).toList(), runs.getTotal()));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/plan")
    @Operation(tags = { "Playground" }, summary = "Plan the next Playground run: what it reuses, where it replays from and where it stops")
    @ApiResponse(responseCode = "404", description = "If the flow revision or the run to build on is not found")
    public PlaygroundPlan planPlaygroundRun(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Body @Valid PlanRequest request) {
        return playgroundService.plan(
            tenantService.resolveTenant(), namespace, id, request.revision(), request.taskId(),
            request.mode() == null ? PlaygroundRunMode.UP_TO : request.mode(), request.buildOn()
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/runs/{executionId}/reused")
    @Operation(tags = { "Playground" }, summary = "For each task run a Playground run reused, the run and task run that first produced it")
    public ReusedResponse listReusedTaskRuns(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The Playground run id") @PathVariable String executionId) {
        return new ReusedResponse(playgroundService.reused(run(namespace, id, executionId)));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/runs/{executionId}/context/{taskRunId}")
    @Operation(tags = { "Playground" }, summary = "The variables a task of a Playground run sees when it renders an expression, secrets masked")
    public ContextResponse getTaskRunContext(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The Playground run id") @PathVariable String executionId,
        @Parameter(description = "The task run id") @PathVariable String taskRunId) throws InternalException {
        return new ContextResponse(playgroundService.context(run(namespace, id, executionId), taskRunId));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/runs/{executionId}/clients/{clientId}")
    @Operation(tags = { "Playground" }, summary = "Report that a Playground panel still shows this run, so it is not killed")
    public ClientResponse reportPlaygroundClient(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The Playground run id") @PathVariable String executionId,
        @Parameter(description = "An id the panel picks once per browser tab") @PathVariable @Pattern(regexp = CLIENT_ID_PATTERN) String clientId) throws QueueException {
        playgroundHeartbeats.report(run(namespace, id, executionId), clientId);
        return new ClientResponse(playgroundHeartbeats.reportEvery().toString());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/runs/{executionId}/clients/{clientId}")
    @Operation(tags = { "Playground" }, summary = "Report that a Playground panel was closed; the run is killed when no other panel shows it")
    public ClientResponse releasePlaygroundClient(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The Playground run id") @PathVariable String executionId,
        @Parameter(description = "An id the panel picks once per browser tab") @PathVariable @Pattern(regexp = CLIENT_ID_PATTERN) String clientId) throws QueueException {
        playgroundHeartbeats.release(run(namespace, id, executionId), clientId);
        return new ClientResponse(playgroundHeartbeats.reportEvery().toString());
    }

    protected Execution run(String namespace, String flowId, String executionId) {
        return playgroundService.run(tenantService.resolveTenant(), namespace, flowId, executionId);
    }

    @Schema(name = "PlaygroundPlanRequest")
    public record PlanRequest(
        @Schema(description = "The flow revision to run, a draft included") @NotNull Integer revision,
        @Schema(description = "The task to run up to or from; none runs the whole flow") @Nullable String taskId,
        @Schema(description = "Whether the run stops before the tasks after taskId, or goes to the end; UP_TO when missing") @Nullable PlaygroundRunMode mode,
        @Schema(description = "LATEST (the default), NOTHING, or the id of one of the caller's runs") @Nullable String buildOn) {
    }

    @Schema(name = "PlaygroundReusedTaskRuns")
    public record ReusedResponse(@NotNull Map<String, ReusedTaskRun> taskRuns) {
    }

    @Schema(name = "PlaygroundTaskRunContext")
    public record ContextResponse(@NotNull Map<String, Object> variables) {
    }

    @Schema(name = "PlaygroundClient")
    public record ClientResponse(@NotNull @Schema(description = "How often the panel should report, as an ISO 8601 duration") String reportEvery) {
    }
}
