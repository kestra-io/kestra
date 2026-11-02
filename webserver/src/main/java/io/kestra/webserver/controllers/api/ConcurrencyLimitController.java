package io.kestra.webserver.controllers.api;

import java.util.Objects;
import java.util.Optional;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ConcurrencyLimitRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
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
    private final ConcurrencyLimitRepositoryInterface concurrencyLimitRepository;
    private final FlowRepositoryInterface flowRepository;
    private final BroadcastQueueInterface<FlowInterface> flowQueue;
    private final TenantService tenantService;

    @Inject
    public ConcurrencyLimitController(
        ConcurrencyLimitRepositoryInterface concurrencyLimitRepository,
        FlowRepositoryInterface flowRepository,
        BroadcastQueueInterface<FlowInterface> flowQueue,
        TenantService tenantService) {
        this.concurrencyLimitRepository = Objects.requireNonNull(concurrencyLimitRepository);
        this.flowRepository = Objects.requireNonNull(flowRepository);
        this.flowQueue = Objects.requireNonNull(flowQueue);
        this.tenantService = Objects.requireNonNull(tenantService);
    }

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
    public HttpResponse<ConcurrencyLimit> updateConcurrencyLimit(String namespace, String flowId, @Body @Valid ConcurrencyLimit concurrencyLimit) throws QueueException {
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
        ConcurrencyLimit updated = concurrencyLimitRepository.update(toUpdate);
        Optional<Flow> flow = flowRepository.findById(tenantId, namespace, flowId, Optional.empty());
        if (flow.isPresent()) {
            flowQueue.emit(flow.get());
        }
        return HttpResponse.ok(updated);
    }
}
