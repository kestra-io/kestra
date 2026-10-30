package io.kestra.webserver.controllers.api;

import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ConcurrencyLimitRepositoryInterface;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.runners.ConcurrencyLimitDataStore;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Controller("/api/v1/{tenant}/concurrency-limit")
public class ConcurrencyLimitController {
    @Inject
    private ConcurrencyLimitRepositoryInterface concurrencyLimitRepository;

    @Inject
    private ConcurrencyLimitDataStore concurrencyLimitDataStore;

    @Inject
    private ExecutionQueuedStateStore executionQueuedStateStore;

    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search")
    @Operation(tags = { "Flows" }, summary = "Search for flow, namespace and tenant concurrency limits")
    public PagedResults<ConcurrencyLimitSearchResult> searchConcurrencyLimits(
        @Parameter(description = "The current page") @QueryValue(value = "page", defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(value = "size", defaultValue = "25") @Min(1) @Max(PageableUtils.MAX_PAGE_SIZE) int size
    ) {
        String tenantId = tenantService.resolveTenant();
        List<ConcurrencyLimit> limits = concurrencyLimitRepository.find(tenantId).stream()
            .sorted(Comparator.comparing(ConcurrencyLimit::getNamespace).thenComparing(ConcurrencyLimit::getFlowId))
            .toList();

        List<ConcurrencyLimitSearchResult> results = limits.stream()
            .skip((long) (page - 1) * size)
            .limit(size)
            .map(limit -> new ConcurrencyLimitSearchResult(
                limit.getTenantId(),
                limit.getNamespace(),
                limit.getFlowId(),
                limit.getRunning(),
                countQueued(tenantId, limit)
            ))
            .toList();
        return PagedResults.of(new ArrayListTotal<>(results, limits.size()));
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
    @Put
    @Operation(tags = { "Flows" }, summary = "Update a flow, namespace or tenant concurrency limit", description = "Leave the namespace and the flow empty to target the tenant limit, and the flow empty to target a namespace limit.")
    public HttpResponse<ConcurrencyLimit> updateConcurrencyLimit(@Body @Valid ConcurrencyLimitUpdateRequest request) {
        String tenantId = tenantService.resolveTenant();
        String namespace = Objects.requireNonNullElse(request.namespace(), "");
        String flowId = Objects.requireNonNullElse(request.flowId(), "");
        if (concurrencyLimitRepository.findById(tenantId, namespace, flowId).isEmpty()) {
            return HttpResponse.notFound();
        }

        return HttpResponse.ok(concurrencyLimitDataStore.update(
            ConcurrencyLimit.builder().tenantId(tenantId).namespace(namespace).flowId(flowId).running(request.running()).build()
        ));
    }

    // Namespace and tenant counters are stored with an empty namespace or flow, which the store reads as a wider scope.
    private int countQueued(String tenantId, ConcurrencyLimit limit) {
        return executionQueuedStateStore.count(
            tenantId,
            limit.getNamespace().isEmpty() ? null : limit.getNamespace(),
            limit.getFlowId().isEmpty() ? null : limit.getFlowId()
        );
    }

    public record ConcurrencyLimitSearchResult(String tenantId, String namespace, String flowId, Integer running, int queued) {}

    public record ConcurrencyLimitUpdateRequest(@Nullable String namespace, @Nullable String flowId, @NotNull @PositiveOrZero Integer running) {}
}
