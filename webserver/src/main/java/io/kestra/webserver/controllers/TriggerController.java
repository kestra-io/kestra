package io.kestra.webserver.controllers;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.services.ConditionService;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
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
    private FlowRepositoryInterface flowRepository;

    @Inject
    private TenantService tenantService;

    @Inject
    private  RunContextFactory runContextFactory;

    @Inject
    private  ConditionService conditionService;

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
            namespace,
            null
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

        trigger = trigger.unlock();
        triggerQueue.emit(trigger);

        return HttpResponse.ok(trigger);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{namespace}/{flowId}")
    @Operation(tags = {"Triggers"}, summary = "Get all triggers for a flow")
    public PagedResults<Trigger> find(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id") @Nullable @QueryValue String flowId
    ) throws HttpStatusException {
        return PagedResults.of(triggerRepository.find(
            PageableUtils.from(page, size, sort, triggerRepository.sortMapping()),
            query,
            tenantService.resolveTenant(),
            namespace,
            flowId
        ));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "/")
    @Operation(tags = {"Triggers"}, summary = "Update a trigger")
    public HttpResponse<Trigger> update(
        @Parameter(description = "The trigger") @Body final Trigger newTrigger
    ) throws HttpStatusException {

        Optional<Flow> maybeFlow = this.flowRepository.findById(this.tenantService.resolveTenant(), newTrigger.getNamespace(), newTrigger.getFlowId());
        if (maybeFlow.isEmpty()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, String.format("Flow of trigger %s not found", newTrigger.getTriggerId()));
        }
        AbstractTrigger abstractTrigger = maybeFlow.get().getTriggers().stream().filter(t -> t.getId().equals(newTrigger.getTriggerId())).findFirst().orElse(null);
        if (abstractTrigger == null) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, String.format("Flow %s has no trigger %s", newTrigger.getFlowId(), newTrigger.getTriggerId()));
        }

        Trigger updatedTrigger = this.triggerRepository.lock(newTrigger.uid(), (current) -> {
            Trigger updated = null;
            try {
                RunContext runContext = runContextFactory.of(maybeFlow.get(), abstractTrigger);
                ConditionContext conditionContext = conditionService.conditionContext(runContext, maybeFlow.get(), null);
                updated = Trigger.update(current, newTrigger, abstractTrigger, conditionContext);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            triggerQueue.emit(updated);

            return updated;
        });

        if (updatedTrigger == null) {

            return HttpResponse.notFound();
        }

        return HttpResponse.ok(updatedTrigger);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "/backfill/pause")
    @Operation(tags = {"Triggers"}, summary = "Pause a backfill")
    public HttpResponse<Trigger> pauseBackfill(
        @Parameter(description = "The trigger") @Body Trigger trigger
    ) {
        Trigger updatedTrigger = this.triggerRepository.lock(trigger.uid(), (current) -> {
            if (current.getBackfill() == null) {
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "No backfill found");
            }
            Trigger updating = current.toBuilder().backfill(current.getBackfill().toBuilder().paused(true).build()).build();
            triggerQueue.emit(updating);

            return updating;
        });

        if (updatedTrigger == null) {

            return HttpResponse.notFound();
        }

        return HttpResponse.ok(updatedTrigger);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "/backfill/unpause")
    @Operation(tags = {"Triggers"}, summary = "Unpause a backfill")
    public HttpResponse<Trigger> unpauseBackfill(
        @Parameter(description = "The trigger") @Body Trigger trigger
    ) {
        Trigger updatedTrigger = this.triggerRepository.lock(trigger.uid(), (current) -> {
            if (current.getBackfill() == null) {
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "No backfill found");
            }
            Trigger updating = current.toBuilder().backfill(current.getBackfill().toBuilder().paused(false).build()).build();
            triggerQueue.emit(updating);

            return updating;
        });

        if (updatedTrigger == null) {

            return HttpResponse.notFound();
        }

        return HttpResponse.ok(updatedTrigger);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "/backfill/delete")
    @Operation(tags = {"Triggers"}, summary = "Delete a backfill")
    public HttpResponse<Trigger> deleteBackfill(
        @Parameter(description = "The trigger") @Body Trigger trigger
    ) {
        Trigger updatedTrigger = this.triggerRepository.lock(trigger.uid(), (current) -> {
            if (current.getBackfill() == null) {
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "No backfill found");
            }
            Trigger updating = current.toBuilder().nextExecutionDate(current.getBackfill().getPreviousNextExecutionDate()).backfill(null).build();
            triggerQueue.emit(updating);

            return updating;
        });

        if (updatedTrigger == null) {

            return HttpResponse.notFound();
        }

        return HttpResponse.ok(updatedTrigger);
    }

}
