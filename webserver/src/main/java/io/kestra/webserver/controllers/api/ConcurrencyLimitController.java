package io.kestra.webserver.controllers.api;

import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.services.ConcurrencyLimitService;
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

@Controller("/api/v1/{tenant}/concurrency-limit")
public class ConcurrencyLimitController {
    @Inject
    private ConcurrencyLimitService  concurrencyLimitService;

    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search")
    @Operation(tags = {"Flows"}, summary = "Search for flow concurrency limits")
    public PagedResults<ConcurrencyLimit> searchConcurrencyLimits() {
        var results = concurrencyLimitService.find(tenantService.resolveTenant());
        return PagedResults.of(new ArrayListTotal<>(results, results.size()));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put("/{namespace}/{flowId}")
    @Operation(tags = {"Flows"}, summary = "Update a flow concurrency limit")
    public HttpResponse<ConcurrencyLimit> updateConcurrencyLimit(@Body ConcurrencyLimit concurrencyLimit) {
        var existing = concurrencyLimitService.findById(concurrencyLimit.getTenantId(), concurrencyLimit.getNamespace(), concurrencyLimit.getFlowId());
        if (existing.isEmpty()) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(concurrencyLimitService.update(concurrencyLimit));
    }
}
