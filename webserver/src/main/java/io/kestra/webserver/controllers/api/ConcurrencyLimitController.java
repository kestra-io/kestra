package io.kestra.webserver.controllers.api;

import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ConcurrencyLimitRepositoryInterface;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(tags = { "Flows" }, summary = "Search for flow concurrency limits")
    public PagedResults<ConcurrencyLimit> searchConcurrencyLimits() {
        var results = concurrencyLimitRepository.find(tenantService.resolveTenant());
        return PagedResults.of(new ArrayListTotal<>(results, results.size()));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{namespace}/{flowId}")
    @Operation(tags = { "Flows" }, summary = "Get the concurrency limit of a flow")
    public HttpResponse<ConcurrencyLimit> getConcurrencyLimit(String namespace, String flowId) {
        return concurrencyLimitRepository.findById(tenantService.resolveTenant(), namespace, flowId)
            .map(HttpResponse::ok)
            .orElseGet(HttpResponse::notFound);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put("/{namespace}/{flowId}")
    @Operation(tags = { "Flows" }, summary = "Update a flow concurrency limit")
    public HttpResponse<ConcurrencyLimit> updateConcurrencyLimit(String namespace, String flowId, @Body @Valid ConcurrencyLimit concurrencyLimit) {
        String tenantId = tenantService.resolveTenant();
        var existing = concurrencyLimitRepository.findById(tenantId, namespace, flowId);
        if (existing.isEmpty()) {
            return HttpResponse.notFound();
        }

        // The path identifies the resource; a body naming another flow must not retarget it.
        ConcurrencyLimit toUpdate = ConcurrencyLimit.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .flowId(flowId)
            .running(concurrencyLimit.getRunning())
            .build();
        return HttpResponse.ok(concurrencyLimitRepository.update(toUpdate));
    }
}
