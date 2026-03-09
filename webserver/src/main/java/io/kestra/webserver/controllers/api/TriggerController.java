package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledTrigger;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.*;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.services.ConditionService;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.responses.BulkResponse;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.CSVUtils;
import io.kestra.webserver.utils.PageableUtils;
import io.kestra.webserver.utils.RequestUtils;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

@Controller("/api/v1/{tenant}/triggers")
@Slf4j
@Validated
public class TriggerController {
    @Inject
    private TriggerRepositoryInterface triggerRepository;

    @Inject
    private QueueInterface<Trigger> triggerQueue;

    @Inject
    private QueueInterface<ExecutionKilled> executionKilledQueue;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private TenantService tenantService;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private ConditionService conditionService;

    @Inject
    private ObjectMapper objectMapper;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search")
    @Operation(tags = {"Triggers"}, summary = "Search for triggers")
    public PagedResults<Triggers> searchTriggers(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "Filters", in = ParameterIn.QUERY) @QueryFilterFormat List<QueryFilter> filters,
        // Deprecated params
        @Parameter(description = "A string filter",deprecated = true) @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix", deprecated = true) @Nullable @QueryValue String namespace,
        @Parameter(description = "The identifier of the worker currently evaluating the trigger", deprecated = true) @Nullable @QueryValue String workerId,
        @Parameter(description = "The flow identifier",deprecated = true) @Nullable @QueryValue String flowId
    ) throws HttpStatusException {
        filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            filters,
            query,
            namespace,
            flowId,
            null,
            null,
            null,
            null,
            null,
            null,
            workerId,
            null);

        ArrayListTotal<Trigger> triggerContexts = triggerRepository.find(
            PageableUtils.from(page, size, sort, triggerRepository.sortMapping()),
            tenantService.resolveTenant(),
            filters

        );

        List<Triggers> triggers = new ArrayList<>();
        triggerContexts.forEach(tc -> {
            Optional<Flow> flow = flowRepository.findById(tc.getTenantId(), tc.getNamespace(), tc.getFlowId());
            if (flow.isEmpty()) {
                // Warn instead of throwing to avoid blocking the trigger UI
                log.warn("Flow {} not found for trigger {}", tc.getFlowId(), tc.getTriggerId());

                return;
            }

            if (flow.get().getTriggers() == null) {
                // a trigger was removed from the flow but still in the trigger table
                return;
            }

            AbstractTrigger abstractTrigger = flow.get().getTriggers().stream().filter(t -> t.getId().equals(tc.getTriggerId())).findFirst().orElse(null);
            if (abstractTrigger == null) {
                // Warn instead of throwing to avoid blocking the trigger UI
                log.warn("Flow {} has no trigger {}", tc.getFlowId(), tc.getTriggerId());
            }

            triggers.add(Triggers.builder()
                .abstractTrigger(abstractTrigger)
                .triggerContext(tc)
                .build()
            );
        });

        return PagedResults.of(new ArrayListTotal<>(triggers, triggerContexts.getTotal()));
    }

    @Builder
    @Getter
    public static class Triggers {
        AbstractTrigger abstractTrigger;
        Trigger triggerContext;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{namespace}/{flowId}/{triggerId}/unlock")
    @Operation(tags = {"Triggers"}, summary = "Unlock a trigger")
    public HttpResponse<Trigger> unlockTrigger(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String flowId,
        @Parameter(description = "The trigger id") @PathVariable String triggerId
    ) throws HttpStatusException, QueueException {
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
    @Post(uri = "/unlock/by-triggers")
    @Operation(tags = {"Triggers"}, summary = "Unlock given triggers")
    public MutableHttpResponse<?> unlockTriggersByIds(
        @Parameter(description = "The triggers to unlock") @Body List<Trigger> triggers
    ) {
        AtomicInteger count = new AtomicInteger();
        triggers.forEach(trigger -> {
            try {
                this.unlockTrigger(trigger.getNamespace(), trigger.getFlowId(), trigger.getTriggerId());
            }
            // When doing bulk action, we ignore that a trigger can't be unlocked
            catch (IllegalStateException | QueueException ignored) {
                return;
            }
            count.getAndIncrement();
        });

        return HttpResponse.ok(BulkResponse.builder().count(count.get()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/unlock/by-query")
    @Operation(tags = {"Triggers"}, summary = "Unlock triggers by query parameters")
    public MutableHttpResponse<?> unlockTriggersByQuery(
        @Parameter(description = "Filters", in = ParameterIn.QUERY) @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter", deprecated = true) @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "A namespace filter prefix", deprecated = true) @Nullable @QueryValue String namespace
    ) {
        filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            filters,
            query,
            namespace,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

        Integer count = triggerRepository
            .findAsync(tenantService.resolveTenant(), filters)
            .filter(trigger -> trigger.getExecutionId() != null || trigger.getEvaluateRunningDate() != null)
            .map(trigger -> {
                try {
                    this.unlockTrigger(trigger.getNamespace(), trigger.getFlowId(), trigger.getTriggerId());
                }
                // When doing bulk action, we ignore that a trigger can't be unlocked
                catch (IllegalStateException | QueueException ignored) {
                    return 0;
                }
                return 1;
            })
            .reduce(Integer::sum)
            .blockOptional()
            .orElse(0);

        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{namespace}/{flowId}")
    @Operation(tags = {"Triggers"}, summary = "Get all triggers for a flow")
    public PagedResults<Trigger> searchTriggersForFlow(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String flowId
    ) throws HttpStatusException {
        return PagedResults.of(triggerRepository.find(
            PageableUtils.from(page, size, sort, triggerRepository.sortMapping()),
            query,
            tenantService.resolveTenant(),
            namespace,
            flowId,
            null
        ));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put
    @Operation(tags = {"Triggers"}, summary = "Update a trigger")
    public HttpResponse<Trigger> updateTrigger(
        @Parameter(description = "The trigger") @Body final Trigger newTrigger
    ) throws HttpStatusException, QueueException {
        newTrigger.setTenantId(tenantService.resolveTenant());
        Optional<Flow> maybeFlow = this.flowRepository.findById(this.tenantService.resolveTenant(), newTrigger.getNamespace(), newTrigger.getFlowId());
        if (maybeFlow.isEmpty()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, String.format("Flow of trigger %s not found", newTrigger.getTriggerId()));
        }
        AbstractTrigger abstractTrigger = maybeFlow.get().getTriggers().stream().filter(t -> t.getId().equals(newTrigger.getTriggerId())).findFirst().orElse(null);
        if (abstractTrigger == null) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, String.format("Flow %s has no trigger %s", newTrigger.getFlowId(), newTrigger.getTriggerId()));
        }

        if (abstractTrigger instanceof RealtimeTriggerInterface) {
            throw new IllegalArgumentException("Realtime triggers can not be updated through the API, please edit the trigger from the flow.");
        }

        Trigger updatedTrigger;

        if (newTrigger.getBackfill() != null) {
            try {
                updatedTrigger = setTriggerBackfill(newTrigger, maybeFlow.get(), abstractTrigger);
            } catch (Exception e) {
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
        } else {
            updatedTrigger = setTriggerDisabled(newTrigger.uid(), newTrigger.getDisabled(), abstractTrigger, maybeFlow.get());
        }

        if (updatedTrigger == null) {
            return HttpResponse.notFound();
        }
        return HttpResponse.ok(updatedTrigger);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{namespace}/{flowId}/{triggerId}/restart")
    @Operation(tags = {"Triggers"}, summary = "Restart a trigger")
    public HttpResponse<?> restartTrigger(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String flowId,
        @Parameter(description = "The trigger id") @PathVariable String triggerId
    ) throws HttpStatusException, QueueException {
        Optional<Trigger> triggerOpt = triggerRepository.findLast(TriggerContext.builder()
            .tenantId(tenantService.resolveTenant())
            .namespace(namespace)
            .flowId(flowId)
            .triggerId(triggerId)
            .build());

        if (triggerOpt.isEmpty()) {
            return HttpResponse.notFound();
        }

        var trigger = triggerOpt.get().toBuilder()
            .workerId(null)
            .evaluateRunningDate(null)
            .date(null)
            .build();

        this.executionKilledQueue.emit(ExecutionKilledTrigger
            .builder()
            .tenantId(trigger.getTenantId())
            .namespace(trigger.getNamespace())
            .flowId(trigger.getFlowId())
            .triggerId(trigger.getTriggerId())
            .build()
        );

        // this will make the trigger restarting
        // be careful that, as everything is asynchronous, it can be restarted before it is killed
        this.triggerQueue.emit(trigger);

        return HttpResponse.ok(trigger);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "/backfill/pause")
    @Operation(tags = {"Triggers"}, summary = "Pause a backfill")
    public HttpResponse<Trigger> pauseBackfill(
        @Parameter(description = "The trigger that need the backfill to be paused") @Body Trigger trigger
    ) throws QueueException {

        return this.setBackfillPaused(trigger, true);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/pause/by-triggers")
    @Operation(tags = {"Triggers"}, summary = "Pause backfill for given triggers")
    public MutableHttpResponse<?> pauseBackfillByIds(
        @Parameter(description = "The triggers that need the backfill to be paused") @Body List<Trigger> triggers
    ) throws QueueException {
        int count = triggers == null ? 0 : backfillsAction(triggers, BACKFILL_ACTION.PAUSE);

        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/pause/by-query")
    @Operation(tags = {"Triggers"}, summary = "Pause backfill for given triggers")
    public MutableHttpResponse<?> pauseBackfillByQuery(
        @Parameter(description = "Filters", in = ParameterIn.QUERY) @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter", deprecated = true) @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "A namespace filter prefix", deprecated = true) @Nullable @QueryValue String namespace
    ) throws QueueException {
        // Updating the backfill within the flux does not works
        List<Trigger> triggers = triggerRepository
            .findAsync(tenantService.resolveTenant(), filters)
            .collectList().block();

        int count = triggers == null ? 0 : backfillsAction(triggers, BACKFILL_ACTION.PAUSE);

        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "/backfill/unpause")
    @Operation(tags = {"Triggers"}, summary = "Unpause a backfill")
    public HttpResponse<Trigger> unpauseBackfill(
        @Parameter(description = "The trigger that need the backfill to be resume") @Body Trigger trigger
    ) throws QueueException {
        return this.setBackfillPaused(trigger, false);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/unpause/by-triggers")
    @Operation(tags = {"Triggers"}, summary = "Unpause backfill for given triggers")
    public MutableHttpResponse<?> unpauseBackfillByIds(
        @Parameter(description = "The triggers that need the backfill to be resume") @Body List<Trigger> triggers
    ) throws QueueException {
        int count = triggers == null ? 0 : backfillsAction(triggers, BACKFILL_ACTION.UNPAUSE);

        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/unpause/by-query")
    @Operation(tags = {"Triggers"}, summary = "Unpause backfill for given triggers")
    public MutableHttpResponse<?> unpauseBackfillByQuery(
        @Parameter(description = "Filters", in = ParameterIn.QUERY) @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter", deprecated = true) @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "A namespace filter prefix", deprecated = true) @Nullable @QueryValue String namespace
    ) throws QueueException {
        filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            filters,
            query,
            namespace,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

        // Updating the backfill within the flux does not works
        List<Trigger> triggers = triggerRepository
            .findAsync(tenantService.resolveTenant(), filters)
            .collectList().block();

        int count = triggers == null ? 0 : backfillsAction(triggers, BACKFILL_ACTION.UNPAUSE);

        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/delete")
    @Operation(tags = {"Triggers"}, summary = "Delete a backfill")
    public HttpResponse<Trigger> deleteBackfill(
        @Parameter(description = "The trigger that need to have its backfill to be deleted") @Body Trigger trigger
    ) throws QueueException {
        Trigger updatedTrigger = this.triggerRepository.lock(trigger.uid(), throwFunction(current -> {
            if (current.getBackfill() == null) {
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "No backfill found");
            }
            Trigger updating = current.toBuilder().nextExecutionDate(current.getBackfill().getPreviousNextExecutionDate()).backfill(null).build();
            triggerQueue.emit(updating);

            return updating;
        }));

        if (updatedTrigger == null) {

            return HttpResponse.notFound();
        }

        return HttpResponse.ok(updatedTrigger);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/delete/by-triggers")
    @Operation(tags = {"Triggers"}, summary = "Delete backfill for given triggers")
    public MutableHttpResponse<?> deleteBackfillByIds(
        @Parameter(description = "The triggers that need the backfill to be deleted") @Body List<Trigger> triggers
    ) throws QueueException {

        int count = triggers == null ? 0 : backfillsAction(triggers, BACKFILL_ACTION.DELETE);

        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/delete/by-query")
    @Operation(tags = {"Triggers"}, summary = "Delete backfill for given triggers")
    public MutableHttpResponse<?> deleteBackfillByQuery(
        @Parameter(description = "Filters", in = ParameterIn.QUERY) @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter", deprecated = true) @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "A namespace filter prefix", deprecated = true) @Nullable @QueryValue String namespace
    ) throws QueueException {
        filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            filters,
            query,
            namespace,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

        // Updating the backfill within the flux does not works
        List<Trigger> triggers = triggerRepository
            .findAsync(tenantService.resolveTenant(), filters)
            .collectList().block();

        int count = triggers == null ? 0 : backfillsAction(triggers, BACKFILL_ACTION.DELETE);

        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/{namespace}/{flowId}/{triggerId}")
    @Operation(tags = {"Triggers"}, summary = "Delete a trigger")
    public MutableHttpResponse<?> deleteTrigger(
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
        triggerRepository.delete(trigger);

        return HttpResponse.noContent();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/delete/by-triggers")
    @Operation(tags = {"Triggers"}, summary = "Delete given triggers")
    public MutableHttpResponse<?> deleteTriggersByIds(
        @Parameter(description = "The triggers to delete") @Body List<Trigger> triggers
    ) {
        AtomicInteger count = new AtomicInteger();
        triggers.forEach(trigger -> {
            try {
                Optional<Trigger> triggerOpt = triggerRepository.findLast(TriggerContext.builder()
                    .tenantId(tenantService.resolveTenant())
                    .namespace(trigger.getNamespace())
                    .flowId(trigger.getFlowId())
                    .triggerId(trigger.getTriggerId())
                    .build());

                if (triggerOpt.isPresent()) {
                    triggerRepository.delete(triggerOpt.get());
                    count.getAndIncrement();
                }
            } catch (Exception ignored) {
            }
        });

        return HttpResponse.ok(BulkResponse.builder().count(count.get()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/delete/by-query")
    @Operation(tags = {"Triggers"}, summary = "Delete triggers by query parameters")
    public MutableHttpResponse<?> deleteTriggersByQuery(
        @Parameter(description = "Filters") @QueryFilterFormat List<QueryFilter> filters
    ) {
        Integer count = triggerRepository
            .findAsync(tenantService.resolveTenant(), filters)
            .map(trigger -> {
                try {
                    triggerRepository.delete(trigger);
                    return 1;
                } catch (Exception ignored) {
                    return 0;
                }
            })
            .reduce(Integer::sum)
            .block();

        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/set-disabled/by-triggers")
    @Operation(tags = {"Triggers"}, summary = "Disable/enable given triggers")
    public MutableHttpResponse<?> disabledTriggersByIds(
        @Parameter(description = "The triggers you want to set the disabled state") @Body @Valid SetDisabledRequest setDisabledRequest
    ) throws QueueException {
        setDisabledRequest.triggers.forEach(throwConsumer(trigger -> this.setTriggerDisabled(trigger, setDisabledRequest.disabled)));

        return HttpResponse.ok(BulkResponse.builder().count(setDisabledRequest.triggers.size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/set-disabled/by-query")
    @Operation(tags = {"Triggers"}, summary = "Disable/enable triggers by query parameters")
    public MutableHttpResponse<?> disabledTriggersByQuery(
        @Parameter(description = "Filters", in = ParameterIn.QUERY) @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter", deprecated = true) @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "A namespace filter prefix", deprecated = true) @Nullable @QueryValue String namespace,

        @Parameter(description = "The disabled state") @QueryValue(defaultValue = "true") Boolean disabled
    ) throws QueueException {
        filters = RequestUtils.getFiltersOrDefaultToLegacyMapping(
            filters,
            query,
            namespace,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

        Integer count = triggerRepository
            .findAsync(tenantService.resolveTenant(), filters)
            .map(throwFunction(trigger -> {
                this.setTriggerDisabled(trigger, disabled);
                return 1;
            }))
            .reduce(Integer::sum)
            .blockOptional()
            .orElse(0);

        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @Get(uri = "/export/by-query/csv", produces = MediaType.TEXT_CSV)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Triggers"}, summary = "Export all triggers as a streamed CSV file")
    @SuppressWarnings("unchecked")
    public MutableHttpResponse<Flux> exportTriggers(
        @Parameter(description = "A list of filters", in = ParameterIn.QUERY) @QueryFilterFormat List<QueryFilter> filters
    ) {

        return HttpResponse.ok(
                CSVUtils.toCSVFlux(
                    triggerRepository.findAsync(this.tenantService.resolveTenant(), filters)
                        .map(log -> objectMapper.convertValue(log, Map.class))
                )
            )
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=triggers.csv");
    }

    public void setTriggerDisabled(Trigger trigger, Boolean disabled) throws QueueException {
        Optional<Flow> maybeFlow = this.flowRepository.findById(this.tenantService.resolveTenant(), trigger.getNamespace(), trigger.getFlowId());

        if (maybeFlow.isEmpty()) {
            return; // Flow doesn't exist
        }

        Optional<AbstractTrigger> maybeAbstractTrigger = maybeFlow.flatMap(flow -> flow.getTriggers().stream().filter(t -> t.getId().equals(trigger.getTriggerId())).findFirst());

        if (maybeAbstractTrigger.isEmpty()) {
            return; // Trigger doesn't exist
        }

        if (maybeAbstractTrigger.get() instanceof RealtimeTriggerInterface) {
            return; // RealTimeTriggers can't be disabled/enabled through API.
        }

        setTriggerDisabled(trigger.uid(), disabled, maybeAbstractTrigger.get(), maybeFlow.get());
    }

    private Trigger setTriggerDisabled(String triggerUID, Boolean disabled, AbstractTrigger triggerDefinition, Flow flow) throws QueueException {
        return this.triggerRepository.lock(triggerUID, throwFunction(current -> {
            if (disabled.equals(current.getDisabled())) {
                return current; // Trigger is already in the expected state
            }
            return doSetTriggerDisabled(current, disabled, flow, triggerDefinition);
        }));
    }

    private Trigger setTriggerBackfill(Trigger newTrigger, Flow flow, AbstractTrigger abstractTrigger) throws Exception {
        return this.triggerRepository.lock(newTrigger.uid(), throwFunction(current -> doSetTriggerBackfill(current, newTrigger.getBackfill(), flow, abstractTrigger)));
    }

    protected Trigger doSetTriggerDisabled(Trigger currentState, Boolean disabled, Flow flow, AbstractTrigger trigger) throws QueueException {
        Trigger.TriggerBuilder<?, ?> builder = currentState.toBuilder().disabled(disabled);

        if (disabled) {
            builder = builder.nextExecutionDate(null);
        }

        Trigger updated = builder.build();
        triggerQueue.emit(updated);
        return updated;
    }

    protected Trigger doSetTriggerBackfill(Trigger currentState, Backfill backfill, Flow flow, AbstractTrigger trigger) throws Exception {
        Trigger updated;
        ZonedDateTime nextExecutionDate = null;

        RunContext runContext = runContextFactory.of(flow, trigger);
        ConditionContext conditionContext = conditionService.conditionContext(runContext, flow, null);

        // We must set up the backfill before the update to calculate the next execution date
        updated = currentState.withBackfill(backfill);

        if (trigger instanceof PollingTriggerInterface pollingTriggerInterface) {
            nextExecutionDate = pollingTriggerInterface.nextEvaluationDate(conditionContext, Optional.of(updated));
        }

        updated = updated
            .toBuilder()
            .nextExecutionDate(nextExecutionDate)
            .build();

        triggerQueue.emit(updated);
        return updated;
    }

    public int backfillsAction(List<Trigger> triggers, BACKFILL_ACTION action) throws QueueException {
        AtomicInteger count = new AtomicInteger();
        triggers.forEach(throwConsumer(trigger -> {
            try {
                switch (action) {
                    case PAUSE:
                        this.pauseBackfill(trigger);
                        break;
                    case UNPAUSE:
                        this.unpauseBackfill(trigger);
                        break;
                    case DELETE:
                        this.deleteBackfill(trigger);
                        break;
                }
                count.getAndIncrement();
            }
            catch(HttpStatusException e) {
                if(e.getStatus().equals(HttpStatus.BAD_REQUEST)) {
                    // When doing bulk action, we ignore trigger that have no backfills
                    return;
                }
                throw e;
            }
        }));

        return count.get();
    }

    public HttpResponse<Trigger> setBackfillPaused(Trigger trigger, Boolean paused) throws QueueException {
        Trigger updatedTrigger = this.triggerRepository.lock(trigger.uid(), throwFunction(current -> {
            if (current.getBackfill() == null) {
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "No backfill found");
            }
            Trigger updating = current.toBuilder().backfill(current.getBackfill().toBuilder().paused(paused).build()).build();
            triggerQueue.emit(updating);

            return updating;
        }));

        if (updatedTrigger == null) {

            return HttpResponse.notFound();
        }

        return HttpResponse.ok(updatedTrigger);
    }

    public record SetDisabledRequest(@NotNull @NotEmpty List<Trigger> triggers, @NotNull Boolean disabled) {
    }

    public enum BACKFILL_ACTION {
        PAUSE,
        UNPAUSE,
        DELETE
    }

}
