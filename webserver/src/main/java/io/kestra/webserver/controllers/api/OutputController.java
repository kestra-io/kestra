package io.kestra.webserver.controllers.api;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.tenant.TenantService;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Controller("/api/v1/{tenant}/outputs")
public class OutputController {
    @Inject
    private TaskOutputService taskOutputService;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private TaskOutputRepositoryInterface taskOutputRepository;

    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{executionId}/{taskRunId}")
    @Operation(tags = { "Outputs" }, summary = "Get task run outputs")
    public Map<String, Object> getTaskRunOutputs(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The task run id") @PathVariable String taskRunId) throws InternalException {
        var execution = executionRepository.findById(tenantService.resolveTenant(), executionId).orElseThrow(NotFoundException::new);
        var taskRun = execution.findTaskRunByTaskRunId(taskRunId);
        return taskOutputService.getOutputs(taskRun);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{executionId}")
    @Operation(tags = { "Outputs" }, summary = "Get task run outputs")
    public List<TaskOutputInformation> getTaskOutputsInformation(@Parameter(description = "The execution id") @PathVariable String executionId) throws InternalException {
        var execution = executionRepository.findById(tenantService.resolveTenant(), executionId).orElseThrow(NotFoundException::new);
        return taskOutputRepository.findByExecution(execution).stream()
            .map(throwFunction(taskOutput -> {
                var taskRun = execution.findTaskRunByTaskRunId(taskOutput.taskRunId());
                return new TaskOutputInformation(
                        taskRun.getTaskId(),
                        taskRun.getId(),
                        taskRun.getValue(),
                        taskRun.getIteration(),
                        taskOutput.value() != null);
                }
            ))
            .toList();
    }

    public record TaskOutputInformation(String taskId, String taskRunId, String value, Integer iteration, boolean inline) {}
}
