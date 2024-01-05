package io.kestra.webserver.controllers;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@Controller("/api/v1/triggers")
public class TriggerController {
    @Inject
    private TriggerRepositoryInterface triggerRepository;

    @Inject
    private QueueInterface<Trigger> triggerQueue;

    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search")
    @Operation(tags = {"Triggers"}, summary = "Search for triggers")
    public PagedResults<Trigger> search(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace
    ) throws HttpStatusException {
        return PagedResults.of(triggerRepository.find(
            PageableUtils.from(page, size, sort, triggerRepository.sortMapping()),
            query,
            tenantService.resolveTenant(),
            namespace
        ));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{namespace}/{flowId}/{triggerId}/unlock")
    @Operation(tags = {"Triggers"}, summary = "Unlock a trigger")
    public HttpResponse<Trigger> unlock(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String flowId,
        @Parameter(description = "The trigger id") @PathVariable String triggerId
    ) throws HttpStatusException {
        Optional<Trigger> triggerOpt = triggerRepository.findLast(TriggerContext.builder()
            .tenantId(tenantService.resolveTenant())
            .namespace(namespace)
            .flowId(flowId)
            .triggerId(triggerId)
            .build());

        if (triggerOpt.isEmpty()) {
            return HttpResponse.notFound();
        }

        Trigger trigger = triggerOpt.get();
        if (trigger.getExecutionId() == null && trigger.getEvaluateRunningDate() == null) {
            throw new IllegalStateException("Trigger is not locked");
        }

        trigger = trigger.resetExecution();
        triggerQueue.emit(trigger);

        return HttpResponse.ok(trigger);
    }
}
