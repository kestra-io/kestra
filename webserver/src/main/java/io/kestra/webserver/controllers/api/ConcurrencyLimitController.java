package io.kestra.webserver.controllers.api;

import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ConcurrencyLimitRepositoryInterface;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.inject.Inject;
import jakarta.validation.Valid;

@Controller("/api/v1/{tenant}/concurrency-limit")
public class ConcurrencyLimitController {
    @Inject
    private ConcurrencyLimitRepositoryInterface concurrencyLimitRepository;

    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search")
    @Operation(tags = { "Flows" }, summary = "Search for concurrency limits")
    public PagedResults<ConcurrencyLimit> searchConcurrencyLimits() {
        var results = concurrencyLimitRepository.find(tenantService.resolveTenant());
        return PagedResults.of(new ArrayListTotal<>(results, results.size()));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put
    @Operation(tags = { "Flows" }, summary = "Update a concurrency limit", description = "The concurrency limit is identified by the namespace and flowId of the body: both are set for a flow scoped limit, flowId is empty for a namespace scoped limit, and both are empty for a tenant scoped limit.")
    public HttpResponse<ConcurrencyLimit> updateConcurrencyLimit(@Body @Valid ConcurrencyLimit concurrencyLimit) {
        String tenantId = tenantService.resolveTenant();
        var existing = concurrencyLimitRepository.findById(tenantId, concurrencyLimit.getNamespace(), concurrencyLimit.getFlowId());
        if (existing.isEmpty()) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(concurrencyLimitRepository.update(new ConcurrencyLimit(tenantId, concurrencyLimit.getNamespace(), concurrencyLimit.getFlowId(), concurrencyLimit.getRunning())));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete
    @Operation(tags = { "Flows" }, summary = "Delete a concurrency limit", description = "Only removes the running counter of the limit: the limit itself, if still defined on the flow, the namespace or the tenant, is re-initialized on the next execution.")
    @ApiResponses(
        @ApiResponse(responseCode = "204", description = "On success")
    )
    public HttpResponse<Void> deleteConcurrencyLimit(
        @Parameter(description = "The namespace of the limit, empty for a tenant scoped limit") @QueryValue(defaultValue = "") String namespace,
        @Parameter(description = "The flow id of the limit, empty for namespace and tenant scoped limits") @QueryValue(defaultValue = "") String flowId) {
        var existing = concurrencyLimitRepository.findById(tenantService.resolveTenant(), namespace, flowId);
        if (existing.isEmpty()) {
            return HttpResponse.notFound();
        }
        concurrencyLimitRepository.delete(existing.get());
        return HttpResponse.noContent();
    }
}
