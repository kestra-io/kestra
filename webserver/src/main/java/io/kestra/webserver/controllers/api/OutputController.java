package io.kestra.webserver.controllers.api;

import java.util.*;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.services.ExecutionOutputService;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.tenant.TenantService;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Controller("/api/v1/{tenant}/outputs")
public class OutputController {
    @Inject
    private TaskOutputService taskOutputService;

    @Inject
    private ExecutionOutputService executionOutputService;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private TaskOutputRepositoryInterface taskOutputRepository;

    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "executions/{executionId}")
    @Operation(tags = { "Outputs" }, summary = "Get the flow-level outputs of an execution")
    @ApiResponse(
        responseCode = "200", description = "The execution outputs as a map of output names to their values",
        content = { @Content(schema = @Schema(type = "object", additionalProperties = Schema.AdditionalPropertiesValue.TRUE)) }
    )
    public Map<String, Object> getExecutionOutputs(@Parameter(description = "The execution id") @PathVariable String executionId) throws InternalException {
        var execution = executionRepository.findById(tenantService.resolveTenant(), executionId).orElseThrow(NotFoundException::new);
        return Optional.ofNullable(executionOutputService.getOutputs(execution)).orElse(Collections.emptyMap());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "tasks/{executionId}/{taskRunId}")
    @Operation(tags = { "Outputs" }, summary = "Get task run outputs")
    @ApiResponse(
        responseCode = "200", description = "The task run outputs as a map of output names to their values",
        content = { @Content(schema = @Schema(type = "object", additionalProperties = Schema.AdditionalPropertiesValue.TRUE)) }
    )
    public Map<String, Object> getTaskRunOutputs(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The task run id") @PathVariable String taskRunId) throws InternalException {
        var execution = executionRepository.findById(tenantService.resolveTenant(), executionId).orElseThrow(NotFoundException::new);
        var taskRun = execution.findTaskRunByTaskRunId(taskRunId);
        return taskOutputService.getOutputs(taskRun);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "tasks/{executionId}")
    @Operation(tags = { "Outputs" }, summary = "List the task runs of an execution having outputs")
    public List<TaskOutputInformation> getTaskOutputsInformation(@Parameter(description = "The execution id") @PathVariable String executionId) throws InternalException {
        var execution = executionRepository.findById(tenantService.resolveTenant(), executionId).orElseThrow(NotFoundException::new);
        return taskOutputRepository.findByExecution(execution).stream()
            .map(throwFunction(taskOutput ->
            {
                var taskRun = execution.findTaskRunByTaskRunId(taskOutput.taskRunId());
                return new TaskOutputInformation(
                    taskRun.getTaskId(),
                    taskRun.getId(),
                    taskRun.getValue(),
                    taskRun.getIteration(),
                    taskOutput.value() != null
                );
            }
            ))
            .toList();
    }

    public record TaskOutputInformation(String taskId, String taskRunId, String value, Integer iteration, boolean inline) {
    }
}
