package io.kestra.webserver.controllers.api;

import io.kestra.core.models.executions.TaskOutput;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.tenant.TenantService;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;

@Controller("/api/v1/{tenant}/outputs")
public class OutputController {
    @Inject
    private TaskOutputRepositoryInterface taskOutputRepository;

    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{executionId}/{taskRunId}")
    @Operation(tags = { "Outputs" }, summary = "Get task run outputs")
    public TaskOutput getTaskRunOutputs(
        @Parameter(description = "The execution id") @PathVariable String executionId, // executionId is used in EE to check RBAC
        @Parameter(description = "The task run id") @PathVariable String taskRunId) {
        return taskOutputRepository.findById(tenantService.resolveTenant(), taskRunId).orElse(null);
    }
}
