package io.kestra.webserver.controllers.api;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.kestra.core.models.Label;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Resource;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.scheduler.events.CreateBackfillTrigger;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.webserver.models.api.ApiAsyncOperationResponse;
import io.kestra.webserver.models.api.ApiTriggerAndState;
import io.kestra.webserver.models.api.ApiTriggerState;
import io.kestra.core.serializers.ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.ListOrMapOfLabelSerializer;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.validations.NoSystemLabelValidation;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.services.TriggerStateService;
import io.kestra.webserver.utils.CSVUtils;
import io.kestra.webserver.utils.PageableUtils;

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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

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
    @Operation(tags = { "Triggers" }, summary = "Search for triggers")
    public PagedResults<ApiTriggerAndState> searchTriggers(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(
            description = "The sort of current page", examples = {
                @ExampleObject(name = "Sort by timestamp in ascending order", value = "timestamp:asc"),
                @ExampleObject(name = "Sort by trigger ID in descending order", value = "triggerId:desc")
            }
        ) @Nullable @QueryValue List<String> sort,
        @Parameter(description = "Filters. PHP-style nested query is used - examples: `filters[flowId][EQUALS]=hello-world`, `filters[namespace][CONTAINS]=test`", in = ParameterIn.QUERY)
        @QueryFilterFormat(Resource.TRIGGER) List<QueryFilter> filters) throws HttpStatusException {
        ArrayListTotal<TriggerState> triggerContexts = triggerRepository.find(
            PageableUtils.from(page, size, sort, triggerRepository.sortMapping()),
            tenantService.resolveTenant(),
            filters

        );

        List<ApiTriggerAndState> triggers = new ArrayList<>();
        triggerContexts.forEach(tc ->
        {
            ApiTriggerAndState result = toApiTriggerAndState(tc);
            if (result != null) {
                triggers.add(result);
            }
        });

        return PagedResults.of(new ArrayListTotal<>(triggers, triggerContexts.getTotal()));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{namespace}/{flowId}")
    @Operation(tags = { "Triggers" }, summary = "Get all triggers for a flow")
    public PagedResults<ApiTriggerState> searchTriggersForFlow(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String flowId) throws HttpStatusException {
        ArrayListTotal<TriggerState> triggerStates = triggerRepository.find(
            PageableUtils.from(page, size, sort, triggerRepository.sortMapping()),
            query,
            tenantService.resolveTenant(),
            namespace,
            flowId,
            null
        );

        List<ApiTriggerState> triggers = triggerStates.stream()
            .map(ApiTriggerState::from)
            .toList();

        return PagedResults.of(new ArrayListTotal<>(triggers, triggerStates.getTotal()));
    }
    // endregion

    // region [Trigger Lock APIs]
    // -----------------------------------------------------------------------------------------------------------------
    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{namespace}/{flowId}/{triggerId}/unlock")
    @Operation(tags = { "Triggers" }, summary = "Unlock a trigger")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = ApiTriggerState.class)) })
    @ApiResponse(responseCode = "409", description = "If the trigger is already unlocked")
    public HttpResponse<ApiTriggerState> unlockTrigger(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String flowId,
        @Parameter(description = "The trigger id") @PathVariable String triggerId) throws HttpStatusException {
        TriggerState state = triggerStateService.unlockTriggerById(
            TriggerId.of(tenantService.resolveTenant(), namespace, flowId, triggerId)
        );
        return HttpResponse.ok(ApiTriggerState.from(state));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/unlock/by-triggers")
    @Operation(tags = { "Triggers" }, summary = "Unlock given triggers asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public MutableHttpResponse<ApiAsyncOperationResponse> unlockTriggersByIds(
        @Parameter(description = "The triggers to unlock") @Body List<ApiTriggerId> triggers) {
        final String tenantId = tenantService.resolveTenant();
        return HttpResponse.accepted().body(
            triggerStateService.unlockAllByIds(toTriggerIds(triggers, tenantId))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/unlock/by-query")
    @Operation(tags = { "Triggers" }, summary = "Unlock triggers by query parameters asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public MutableHttpResponse<ApiAsyncOperationResponse> unlockTriggersByQuery(
        @Parameter(description = "Filters. PHP-style nested query is used - examples: `filters[flowId][EQUALS]=hello-world`, `filters[namespace][CONTAINS]=test`", in = ParameterIn.QUERY)
        @QueryFilterFormat(Resource.TRIGGER) List<QueryFilter> filters) {
        return HttpResponse.accepted().body(
            triggerStateService.unlockAllMatching(tenantService.resolveTenant(), filters)
        );
    }
    // endregion

    // region [Restart APIs]
    // -----------------------------------------------------------------------------------------------------------------
    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/{namespace}/{flowId}/{triggerId}/restart")
    @Operation(tags = { "Triggers" }, summary = "Restart a trigger")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = ApiTriggerState.class)) })
    @ApiResponse(responseCode = "409", description = "If the trigger cannot be restarted")
    public HttpResponse<ApiTriggerState> restartTrigger(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String flowId,
        @Parameter(description = "The trigger id") @PathVariable String triggerId) throws HttpStatusException, QueueException {
        TriggerState state = triggerStateService.resetTrigger(
            TriggerId.of(tenantService.resolveTenant(), namespace, flowId, triggerId)
        );
        return HttpResponse.ok(ApiTriggerState.from(state));
    }
    // endregion

    // region [Backfill APIs]
    // -----------------------------------------------------------------------------------------------------------------
    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "/backfill/create")
    @Operation(tags = { "Triggers" }, summary = "Create a backfill")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = ApiTriggerState.class)) })
    @ApiResponse(responseCode = "409", description = "If the backfill cannot be created")
    public HttpResponse<ApiTriggerState> createBackfill(
        @Parameter(description = "The trigger that need the backfill to be created") @Body ApiCreateBackfillRequest request) {
        TriggerId triggerId = TriggerId.of(tenantService.resolveTenant(), request.namespace(), request.flowId(), request.triggerId());
        CreateBackfillTrigger.Backfill backfill = new CreateBackfillTrigger.Backfill(
            request.backfill().start(), request.backfill().end(), request.backfill().inputs(), request.backfill().labels()
        );
        TriggerState state = triggerStateService.createBackfill(triggerId, backfill);
        return HttpResponse.ok(ApiTriggerState.from(state));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "/backfill/pause")
    @Operation(tags = { "Triggers" }, summary = "Pause a backfill")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = ApiTriggerState.class)) })
    @ApiResponse(responseCode = "409", description = "If the backfill cannot be paused")
    public HttpResponse<ApiTriggerState> pauseBackfill(
        @Parameter(description = "The trigger that need the backfill to be paused") @Body ApiTriggerId trigger) {
        TriggerState state = triggerStateService.setBackfillPaused(trigger.toTriggerId(tenantService.resolveTenant()), true);
        return HttpResponse.ok(ApiTriggerState.from(state));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/pause/by-triggers")
    @Operation(tags = { "Triggers" }, summary = "Pause backfill for given triggers asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public MutableHttpResponse<ApiAsyncOperationResponse> pauseBackfillByIds(
        @Parameter(description = "The triggers that need the backfill to be paused") @Body List<ApiTriggerId> triggers) {
        return HttpResponse.accepted().body(
            triggerStateService.pauseAllBackfillsByIds(toTriggerIds(triggers, tenantService.resolveTenant()))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/pause/by-query")
    @Operation(tags = { "Triggers" }, summary = "Pause backfill for triggers matching query asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public MutableHttpResponse<ApiAsyncOperationResponse> pauseBackfillByQuery(
        @Parameter(description = "Filters. PHP-style nested query is used - examples: `filters[flowId][EQUALS]=hello-world`, `filters[namespace][CONTAINS]=test`", in = ParameterIn.QUERY)
        @QueryFilterFormat(Resource.TRIGGER) List<QueryFilter> filters) {
        return HttpResponse.accepted().body(
            triggerStateService.pauseAllBackfillsMatching(tenantService.resolveTenant(), filters)
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "/backfill/unpause")
    @Operation(tags = { "Triggers" }, summary = "Unpause a backfill")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = ApiTriggerState.class)) })
    @ApiResponse(responseCode = "409", description = "If the backfill cannot be resumed")
    public HttpResponse<ApiTriggerState> unpauseBackfill(
        @Parameter(description = "The trigger that need the backfill to be resume") @Body ApiTriggerId trigger) {
        TriggerState state = triggerStateService.setBackfillPaused(trigger.toTriggerId(tenantService.resolveTenant()), false);
        return HttpResponse.ok(ApiTriggerState.from(state));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/unpause/by-triggers")
    @Operation(tags = { "Triggers" }, summary = "Unpause backfill for given triggers asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public MutableHttpResponse<ApiAsyncOperationResponse> unpauseBackfillByIds(
        @Parameter(description = "The triggers that need the backfill to be resume") @Body List<ApiTriggerId> triggers) {
        return HttpResponse.accepted().body(
            triggerStateService.resumeAllBackfillsByIds(toTriggerIds(triggers, tenantService.resolveTenant()))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/unpause/by-query")
    @Operation(tags = { "Triggers" }, summary = "Unpause backfill for triggers matching query asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public MutableHttpResponse<ApiAsyncOperationResponse> unpauseBackfillByQuery(
        @Parameter(description = "Filters. PHP-style nested query is used - examples: `filters[flowId][EQUALS]=hello-world`, `filters[namespace][CONTAINS]=test`", in = ParameterIn.QUERY)
        @QueryFilterFormat(Resource.TRIGGER) List<QueryFilter> filters) {
        return HttpResponse.accepted().body(
            triggerStateService.resumeAllBackfillsMatching(tenantService.resolveTenant(), filters)
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/delete")
    @Operation(tags = { "Triggers" }, summary = "Delete a backfill")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = ApiTriggerState.class)) })
    @ApiResponse(responseCode = "409", description = "If the backfill cannot be deleted")
    public HttpResponse<ApiTriggerState> deleteBackfill(
        @Parameter(description = "The trigger that need to have its backfill to be deleted") @Body ApiTriggerId trigger) {
        TriggerState state = triggerStateService.deleteBackfill(trigger.toTriggerId(tenantService.resolveTenant()));
        return HttpResponse.ok(ApiTriggerState.from(state));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/delete/by-triggers")
    @Operation(tags = { "Triggers" }, summary = "Delete backfill for given triggers asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public MutableHttpResponse<ApiAsyncOperationResponse> deleteBackfillByIds(
        @Parameter(description = "The triggers that need the backfill to be deleted") @Body List<ApiTriggerId> triggers) {
        return HttpResponse.accepted().body(
            triggerStateService.deleteAllBackfillsByIds(toTriggerIds(triggers, tenantService.resolveTenant()))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/backfill/delete/by-query")
    @Operation(tags = { "Triggers" }, summary = "Delete backfill for triggers matching query asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public MutableHttpResponse<ApiAsyncOperationResponse> deleteBackfillByQuery(
        @Parameter(description = "Filters. PHP-style nested query is used - examples: `filters[flowId][EQUALS]=hello-world`, `filters[namespace][CONTAINS]=test`", in = ParameterIn.QUERY)
        @QueryFilterFormat(Resource.TRIGGER) List<QueryFilter> filters) {
        return HttpResponse.accepted().body(
            triggerStateService.deleteAllBackfillsMatching(tenantService.resolveTenant(), filters)
        );
    }
    //endregion

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/{namespace}/{flowId}/{triggerId}")
    @Operation(tags = { "Triggers" }, summary = "Delete a trigger")
    @ApiResponse(responseCode = "204", description = "On success")
    @ApiResponse(responseCode = "409", description = "If the trigger cannot be deleted")
    public HttpResponse<Void> deleteTrigger(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String flowId,
        @Parameter(description = "The trigger id") @PathVariable String triggerId) throws HttpStatusException {
        triggerStateService.deleteById(TriggerId.of(tenantService.resolveTenant(), namespace, flowId, triggerId));
        return HttpResponse.noContent();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/delete/by-triggers")
    @Operation(tags = { "Triggers" }, summary = "Delete given triggers asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public MutableHttpResponse<ApiAsyncOperationResponse> deleteTriggersByIds(
        @Parameter(description = "The triggers to delete") @Body List<ApiTriggerId> triggers) {
        return HttpResponse.accepted().body(
            triggerStateService.deleteAllByIds(toTriggerIds(triggers, tenantService.resolveTenant()))
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/delete/by-query")
    @Operation(tags = { "Triggers" }, summary = "Delete triggers by query parameters asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public MutableHttpResponse<ApiAsyncOperationResponse> deleteTriggersByQuery(
        @Parameter(description = "Filters. PHP-style nested query is used - examples: `filters[flowId][EQUALS]=hello-world`, `filters[namespace][CONTAINS]=test`")
        @QueryFilterFormat(Resource.TRIGGER) List<QueryFilter> filters) {
        return HttpResponse.accepted().body(
            triggerStateService.deleteAllMatching(tenantService.resolveTenant(), filters)
        );
    }

    // region [Disabled APIs]
    // -----------------------------------------------------------------------------------------------------------------
    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "/set-disabled")
    @Operation(tags = { "Triggers" }, summary = "Disable/enable a trigger")
    @ApiResponse(responseCode = "200", description = "On success", content = { @Content(schema = @Schema(implementation = ApiTriggerState.class)) })
    @ApiResponse(responseCode = "409", description = "If the trigger state cannot be changed")
    public HttpResponse<ApiTriggerState> disableTriggerById(
        @Parameter(description = "The trigger") @Body final ApiDisableTriggerRequest request) throws HttpStatusException {
        TriggerId triggerId = TriggerId.of(tenantService.resolveTenant(), request.namespace(), request.flowId(), request.triggerId());
        TriggerState state = triggerStateService.toggleTriggerById(triggerId, request.disabled());
        return HttpResponse.ok(ApiTriggerState.from(state));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/set-disabled/by-triggers")
    @Operation(tags = { "Triggers" }, summary = "Disable/enable given triggers asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public MutableHttpResponse<ApiAsyncOperationResponse> disabledTriggersByIds(
        @Parameter(description = "The triggers you want to set the disabled state") @Body @Valid SetDisabledRequest request) {
        return HttpResponse.accepted().body(
            triggerStateService.toggleAllByIds(toTriggerIds(request.triggers(), tenantService.resolveTenant()), request.disabled())
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/set-disabled/by-query")
    @Operation(tags = { "Triggers" }, summary = "Disable/enable triggers by query parameters asynchronously")
    @ApiResponse(responseCode = "202", description = "Accepted", content = { @Content(schema = @Schema(implementation = ApiAsyncOperationResponse.class)) })
    public MutableHttpResponse<ApiAsyncOperationResponse> disabledTriggersByQuery(
        @Parameter(description = "Filters. PHP-style nested query is used - examples: `filters[flowId][EQUALS]=hello-world`, `filters[namespace][CONTAINS]=test`", in = ParameterIn.QUERY)
        @QueryFilterFormat(Resource.TRIGGER) List<QueryFilter> filters,
        @Parameter(description = "The disabled state") @QueryValue(defaultValue = "true") Boolean disabled) {
        return HttpResponse.accepted().body(
            triggerStateService.toggleAllMatching(tenantService.resolveTenant(), filters, disabled)
        );
    }
    // endregion

    @Get(uri = "/export/by-query/csv", produces = MediaType.TEXT_CSV)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Triggers" }, summary = "Export all triggers as a streamed CSV file")
    @SuppressWarnings("unchecked")
    public MutableHttpResponse<Flux<String>> exportTriggers(
        @Parameter(description = "Filters. PHP-style nested query is used - examples: `filters[flowId][EQUALS]=hello-world`, `filters[namespace][CONTAINS]=test`", in = ParameterIn.QUERY)
        @QueryFilterFormat(Resource.TRIGGER) List<QueryFilter> filters) {

        return HttpResponse.ok(
            CSVUtils.toCSVFlux(
                triggerRepository.find(this.tenantService.resolveTenant(), filters)
                    .map(log -> objectMapper.convertValue(log, JacksonMapper.MAP_TYPE_REFERENCE))
            )
        )
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=triggers.csv");
    }

    private static List<TriggerId> toTriggerIds(List<ApiTriggerId> triggers, String tenantId) {
        return triggers.stream().map(t -> t.toTriggerId(tenantId)).toList();
    }

    private ApiTriggerAndState toApiTriggerAndState(TriggerState tc) {
        Optional<Flow> flow = flowRepository.findById(tc.getTenantId(), tc.getNamespace(), tc.getFlowId());
        if (flow.isEmpty()) {
            log.warn("Flow not found for trigger: {}", TriggerId.of(tc));
            return null;
        }

        if (flow.get().getTriggers() == null) {
            return null;
        }

        AbstractTrigger trigger = flow.get().getTriggers().stream()
            .filter(t -> t.getId().equals(tc.getTriggerId()))
            .findFirst()
            .orElse(null);

        if (trigger == null) {
            log.warn("Flow {} has no trigger {}", tc.getFlowId(), tc.getTriggerId());
        }

        return ApiTriggerAndState.builder()
            .trigger(trigger)
            .state(ApiTriggerState.from(tc))
            .build();
    }

    public record SetDisabledRequest(
        @NotNull @NotEmpty List<ApiTriggerId> triggers,
        @NotNull Boolean disabled) {
    }

    public record ApiCreateBackfillRequest(
        @Parameter(description = "The namespace.") String namespace,
        @Parameter(description = "The ID of the flow.") String flowId,
        @Parameter(description = "The ID of the trigger.") String triggerId,
        @Parameter(description = "The backfill configuration") Backfill backfill) {

        public record Backfill(
            ZonedDateTime start,
            ZonedDateTime end,
            Map<String, Object> inputs,
            @JsonSerialize(using = ListOrMapOfLabelSerializer.class)
            @JsonDeserialize(using = ListOrMapOfLabelDeserializer.class) List<@NoSystemLabelValidation Label> labels) {
        }
    }

    public record ApiDisableTriggerRequest(
        @Parameter(description = "The namespace.") String namespace,
        @Parameter(description = "The ID of the flow.") String flowId,
        @Parameter(description = "The ID of the trigger.") String triggerId,
        @Parameter(description = "Specifies whether trigger should be disabled") boolean disabled) {
    }

    public record ApiTriggerId(
        @Parameter(description = "The namespace.") String namespace,
        @Parameter(description = "The ID of the flow.") String flowId,
        @Parameter(description = "The ID of the trigger.") String triggerId) {

        public TriggerId toTriggerId(final String tenant) {
            return TriggerId.of(tenant, namespace, flowId, triggerId);
        }
    }
}
