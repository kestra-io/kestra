package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.kestra.core.models.Label;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.serializers.ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.ListOrMapOfLabelSerializer;
import io.kestra.core.services.TriggerStateService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.validations.NoSystemLabelValidation;
import io.kestra.core.scheduler.events.CreateBackfillTrigger;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.responses.BulkResponse;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.CSVUtils;
import io.kestra.webserver.utils.PageableUtils;
import io.kestra.webserver.utils.RequestUtils;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
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

@Controller("/api/v1/{tenant}/triggers")
@Slf4j
public class TriggerController {

    @Inject
    private TriggerRepositoryInterface triggerRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private TenantService tenantService;

    @Inject
    private TriggerStateService triggerStateService;

    @Inject
    private ObjectMapper objectMapper;

    // region [Trigger Search APIs]
    // -----------------------------------------------------------------------------------------------------------------
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

        ArrayListTotal<TriggerState> triggerContexts = triggerRepository.find(
            PageableUtils.from(page, size, sort, triggerRepository.sortMapping()),
            tenantService.resolveTenant(),
            filters

        );

        List<Triggers> triggers = new ArrayList<>();
        triggerContexts.forEach(tc -> {
            Optional<Flow> flow = flowRepository.findById(tc.getTenantId(), tc.getNamespace(), tc.getFlowId());
            if (flow.isEmpty()) {
                // Warn instead of throwing to avoid blocking the trigger UI
                log.warn("Flow not found for trigger: {}",  TriggerId.of(tc));

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

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{namespace}/{flowId}")
    @Operation(tags = {"Triggers"}, summary = "Get all triggers for a flow")
    public PagedResults<TriggerState> searchTriggersForFlow(
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
    // endregion

    @Builder
    @Getter
    public static class Triggers {
        AbstractTrigger abstractTrigger;
        TriggerState triggerContext;
    }

    // region [Trigger Lock APIs]
    // -----------------------------------------------------------------------------------------------------------------
    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{namespace}/{flowId}/{triggerId}/unlock")
    @Operation(tags = {"Triggers"}, summary = "Unlock a trigger. Trigger will be unlocked asynchronously")
    public HttpResponse<?> unlockTrigger(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String flowId,
        @Parameter(description = "The trigger id") @PathVariable String triggerId
    ) throws HttpStatusException {
        triggerStateService.unlockTriggerById(TriggerId.of(tenantService.resolveTenant(), namespace, flowId, triggerId));
        return HttpResponse.noContent();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/unlock/by-triggers")
    @Operation(tags = {"Triggers"}, summary = "Unlock given triggers")
    public MutableHttpResponse<?> unlockTriggersByIds(
        @Parameter(description = "The triggers to unlock") @Body List<ApiTriggerId> triggers
    ) {
        final String tenantId = tenantService.resolveTenant();
        int count = triggerStateService.unlockAllTriggersByIds(triggers.stream()
            .map(trigger -> TriggerId.of(tenantId, trigger.namespace(), trigger.flowId(), trigger.triggerId()))
            .toList());
        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/unlock/by-query")
    @Operation(tags = {"Triggers"}, summary = "Unlock triggers by query parameters")
    public MutableHttpResponse<?> unlockTriggersByQuery(
        @Parameter(description = "Filters", in = ParameterIn.QUERY) @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter", deprecated = true) @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "A namespace filter prefix", deprecated = true) @Nullable @QueryValue String namespace
    ) {
        filters = getFiltersOrDefaultToLegacyMapping(filters, query, namespace);

        int count = triggerStateService.unlockAllTriggersMatching(this.tenantService.resolveTenant(), filters);
        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }
    // endregion


    // region [Restart APIs]
    // -----------------------------------------------------------------------------------------------------------------
    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{namespace}/{flowId}/{triggerId}/restart")
    @Operation(tags = {"Triggers"}, summary = "Restart a trigger")
    public HttpResponse<?> restartTrigger(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String flowId,
        @Parameter(description = "The trigger id") @PathVariable String triggerId
    ) throws HttpStatusException, QueueException {
        triggerStateService.resetTrigger(TriggerId.of(tenantService.resolveTenant(), namespace, flowId, triggerId));
        return HttpResponse.noContent();
    }
    // endregion

    // region [Backfill APIs]
    // -----------------------------------------------------------------------------------------------------------------
    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "/backfill/create")
    @Operation(tags = {"Triggers"}, summary = "Create a backfill")
    public HttpResponse<Void> createBackfill(
        @Parameter(description = "The trigger that need the backfill to be created") @Body ApiCreateBackfillRequest request
    ) {
        TriggerId triggerId = TriggerId.of(tenantService.resolveTenant(), request.namespace(), request.flowId(), request.triggerId());
        CreateBackfillTrigger.Backfill backfill = new CreateBackfillTrigger.Backfill(request.backfill().start(), request.backfill().end(), request.backfill().inputs(), request.backfill().labels());
        triggerStateService.createBackfill(triggerId, backfill);
        return HttpResponse.noContent();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "/backfill/pause")
    @Operation(tags = {"Triggers"}, summary = "Pause a backfill")
    public HttpResponse<Void> pauseBackfill(
        @Parameter(description = "The trigger that need the backfill to be paused") @Body ApiTriggerId trigger
    ) {
        triggerStateService.setBackfillPaused(trigger.toTriggerId(tenantService.resolveTenant()), true);
        return HttpResponse.noContent();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/pause/by-triggers")
    @Operation(tags = {"Triggers"}, summary = "Pause backfill for given triggers")
    public MutableHttpResponse<?> pauseBackfillByIds(
        @Parameter(description = "The triggers that need the backfill to be paused") @Body List<ApiTriggerId> triggers
    ) {
        int count = triggerStateService.pauseAllBackfillByIds(triggers.stream().map(it -> it.toTriggerId(tenantService.resolveTenant())).toList());
        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/pause/by-query")
    @Operation(tags = {"Triggers"}, summary = "Pause backfill for given triggers")
    public MutableHttpResponse<?> pauseBackfillByQuery(
        @Parameter(description = "Filters", in = ParameterIn.QUERY) @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter", deprecated = true) @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "A namespace filter prefix", deprecated = true) @Nullable @QueryValue String namespace
    ) {
        int count = triggerStateService.pauseAllBackfillMatching(tenantService.resolveTenant(), getFiltersOrDefaultToLegacyMapping(filters, query, namespace));
        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "/backfill/unpause")
    @Operation(tags = {"Triggers"}, summary = "Unpause a backfill")
    public HttpResponse<TriggerState> unpauseBackfill(
        @Parameter(description = "The trigger that need the backfill to be resume") @Body ApiTriggerId trigger
    ) {
        triggerStateService.setBackfillPaused(trigger.toTriggerId(tenantService.resolveTenant()), false);
        return HttpResponse.noContent();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/unpause/by-triggers")
    @Operation(tags = {"Triggers"}, summary = "Unpause backfill for given triggers")
    public MutableHttpResponse<?> unpauseBackfillByIds(
        @Parameter(description = "The triggers that need the backfill to be resume") @Body List<ApiTriggerId> triggers
    ) {
        int count = triggerStateService.resumeAllBackfillByIds(triggers.stream().map(it -> it.toTriggerId(tenantService.resolveTenant())).toList());
        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/unpause/by-query")
    @Operation(tags = {"Triggers"}, summary = "Unpause backfill for given triggers")
    public MutableHttpResponse<?> unpauseBackfillByQuery(
        @Parameter(description = "Filters", in = ParameterIn.QUERY) @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter", deprecated = true) @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "A namespace filter prefix", deprecated = true) @Nullable @QueryValue String namespace
    ) {
        int count = triggerStateService.resumeAllBackfillMatching(tenantService.resolveTenant(), getFiltersOrDefaultToLegacyMapping(filters, query, namespace));
        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/delete")
    @Operation(tags = {"Triggers"}, summary = "Delete a backfill")
    public HttpResponse<Void> deleteBackfill(
        @Parameter(description = "The trigger that need to have its backfill to be deleted") @Body ApiTriggerId trigger
    ) {
        triggerStateService.deleteBackfill(trigger.toTriggerId(tenantService.resolveTenant()));
        return HttpResponse.noContent();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/delete/by-triggers")
    @Operation(tags = {"Triggers"}, summary = "Delete backfill for given triggers")
    public MutableHttpResponse<?> deleteBackfillByIds(
        @Parameter(description = "The triggers that need the backfill to be deleted") @Body List<ApiTriggerId> triggers
    ) {
        int count = triggerStateService.deleteAllBackfills(triggers.stream().map(it -> it.toTriggerId(tenantService.resolveTenant())).toList());
        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/delete/by-query")
    @Operation(tags = {"Triggers"}, summary = "Delete backfill for given triggers")
    public MutableHttpResponse<?> deleteBackfillByQuery(
        @Parameter(description = "Filters", in = ParameterIn.QUERY) @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter", deprecated = true) @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "A namespace filter prefix", deprecated = true) @Nullable @QueryValue String namespace
    ) {
        int count = triggerStateService.deleteBackfillMatching(tenantService.resolveTenant(), getFiltersOrDefaultToLegacyMapping(filters, query, namespace));
        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }
    //endregion

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/{namespace}/{flowId}/{triggerId}")
    @Operation(tags = {"Triggers"}, summary = "Delete a trigger")
    public HttpResponse<Void> deleteTrigger(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String flowId,
        @Parameter(description = "The trigger id") @PathVariable String triggerId
    ) throws HttpStatusException {
        triggerStateService.deleteById(TriggerId.of(tenantService.resolveTenant(), namespace, flowId, triggerId));
        return HttpResponse.noContent();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/delete/by-triggers")
    @Operation(tags = {"Triggers"}, summary = "Delete given triggers")
    public MutableHttpResponse<?> deleteTriggersByIds(
        @Parameter(description = "The triggers to delete") @Body List<ApiTriggerId> triggers
    ) {
        List<TriggerId> ids = triggers.stream().map(it -> it.toTriggerId(tenantService.resolveTenant())).toList();
        int count = triggerStateService.deleteByIdyIds(ids);
        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/delete/by-query")
    @Operation(tags = {"Triggers"}, summary = "Delete triggers by query parameters")
    public MutableHttpResponse<?> deleteTriggersByQuery(
        @Parameter(description = "Filters") @QueryFilterFormat List<QueryFilter> filters
    ) {
        int count = triggerStateService.deleteAllTriggersMatching(tenantService.resolveTenant(), filters);
        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }
    // region [Disabled APIs]
    // -----------------------------------------------------------------------------------------------------------------

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "/set-disabled")
    @Operation(tags = {"Triggers"}, summary = "Disable/enable a trigger")
    public HttpResponse<Void> disableTriggerById(
        @Parameter(description = "The trigger") @Body final ApiDisableTriggerRequest request
    ) throws HttpStatusException {
        TriggerId triggerId = TriggerId.of(tenantService.resolveTenant(), request.namespace(), request.flowId(), request.triggerId());
        triggerStateService.toggleTriggerById(triggerId, request.disabled());
        return HttpResponse.noContent();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/set-disabled/by-triggers")
    @Operation(tags = {"Triggers"}, summary = "Disable/enable given triggers")
    public MutableHttpResponse<?> disabledTriggersByIds(
        @Parameter(description = "The triggers you want to set the disabled state") @Body @Valid SetDisabledRequest request
    ) {
        request.triggers().forEach(trigger ->
            triggerStateService.toggleTriggerById(trigger.toTriggerId(tenantService.resolveTenant()), request.disabled)
        );
        return HttpResponse.ok(BulkResponse.builder().count(request.triggers.size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/set-disabled/by-query")
    @Operation(tags = {"Triggers"}, summary = "Disable/enable triggers by query parameters")
    public MutableHttpResponse<?> disabledTriggersByQuery(
        @Parameter(description = "Filters", in = ParameterIn.QUERY) @QueryFilterFormat List<QueryFilter> filters,

        @Deprecated @Parameter(description = "A string filter", deprecated = true) @Nullable @QueryValue(value = "q") String query,
        @Deprecated @Parameter(description = "A namespace filter prefix", deprecated = true) @Nullable @QueryValue String namespace,

        @Parameter(description = "The disabled state") @QueryValue(defaultValue = "true") Boolean disabled
    ) {

        Integer count = triggerStateService.toggleAllTriggersMatching(tenantService.resolveTenant(), getFiltersOrDefaultToLegacyMapping(filters, query, namespace), disabled);
        return HttpResponse.ok(BulkResponse.builder().count(count).build());
    }
    // endregion

    @Get(uri = "/export/by-query/csv", produces = MediaType.TEXT_CSV)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Triggers"}, summary = "Export all triggers as a streamed CSV file")
    @SuppressWarnings("unchecked")
    public MutableHttpResponse<Flux> exportTriggers(
        @Parameter(description = "A list of filters", in = ParameterIn.QUERY) @QueryFilterFormat List<QueryFilter> filters
    ) {

        return HttpResponse.ok(
                CSVUtils.toCSVFlux(
                    triggerRepository.find(this.tenantService.resolveTenant(), filters)
                        .map(log -> objectMapper.convertValue(log, Map.class))
                )
            )
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=triggers.csv");
    }

    public record SetDisabledRequest(
        @NotNull @NotEmpty
        List<ApiTriggerId> triggers,
        @NotNull
        Boolean disabled
    ) {
    }

    public record ApiCreateBackfillRequest(
        @Parameter(description = "The namespace.")
        String namespace,
        @Parameter(description = "The ID of the flow.")
        String flowId,
        @Parameter(description = "The ID of the trigger.")
        String triggerId,
        @Parameter(description = "Specifies whether trigger should be disabled")
        Backfill backfill
    ){

        public record Backfill(
            ZonedDateTime start,
            ZonedDateTime end,
            Map<String, Object> inputs,
            @JsonSerialize(using = ListOrMapOfLabelSerializer.class)
            @JsonDeserialize(using = ListOrMapOfLabelDeserializer.class)
            List<@NoSystemLabelValidation Label> labels
        ) {
        }
    }

    public record ApiDisableTriggerRequest(
        @Parameter(description = "The namespace.")
        String namespace,
        @Parameter(description = "The ID of the flow.")
        String flowId,
        @Parameter(description = "The ID of the trigger.")
        String triggerId,
        @Parameter(description = "Specifies whether trigger should be disabled")
        boolean disabled
    ){}

    public record ApiTriggerId(
        @Parameter(description = "The namespace.")
        String namespace,
        @Parameter(description = "The ID of the flow.")
        String flowId,
        @Parameter(description = "The ID of the trigger.")
        String triggerId
    ){

        public TriggerId toTriggerId(final String tenant) {
            return TriggerId.of(tenant, namespace, flowId, triggerId);
        }
    }

    private static List<QueryFilter> getFiltersOrDefaultToLegacyMapping(List<QueryFilter> filters, String query, String namespace) {
        return RequestUtils.getFiltersOrDefaultToLegacyMapping(filters, query, namespace, null,null, null, null, null, null, null, null, null);
    }

}
