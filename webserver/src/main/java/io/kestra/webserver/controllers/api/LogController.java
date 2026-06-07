package io.kestra.webserver.controllers.api;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.kestra.webserver.utils.QueryFilterUtils;
import org.slf4j.event.Level;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Resource;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.runners.FollowLogEvent;
import io.kestra.core.services.ExecutionLogService;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.LogStreamingService;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.sse.Event;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Min;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

@Controller("/api/v1/{tenant}/logs")
@Requires(beans = LogRepositoryInterface.class)
public class LogController {
    @Inject
    private LogRepositoryInterface logRepository;

    @Inject
    private ExecutionLogService logService;

    @Inject
    private TenantService tenantService;

    @Inject
    private LogStreamingService logStreamingService;
    @Inject
    private ExecutionService executionService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search")
    @Operation(tags = { "Logs" }, summary = "Search for logs")
    public PagedResults<LogEntry> searchLogs(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(
            description = "The sort of current page", examples = {
                @ExampleObject(name = "Sort by timestamp in ascending order", value = "timestamp:asc")
            }
        ) @Nullable @QueryValue List<String> sort,
        @Parameter(
            description = "Filters. PHP-style nested query is used - examples: `filters[flowId][EQUALS]=hello-world`, `filters[timeRange][EQUALS]=P7D`, `filters[level][EQUALS]=DEBUG`",
            in = ParameterIn.QUERY
        ) @Nullable @QueryFilterFormat(Resource.LOG) List<QueryFilter> filters)
        throws HttpStatusException {
        return PagedResults.of(
            logRepository.find(
                PageableUtils.from(page, size, sort),
                tenantService.resolveTenant(),
                QueryFilterUtils.replaceTimeRangeWithComputedStartDateFilter(filters)
            )
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}")
    @Operation(tags = { "Logs" }, summary = "Get logs for a specific execution, taskrun or task")
    public List<LogEntry> listLogsFromExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "Filters") @Nullable @QueryFilterFormat(Resource.LOG) List<QueryFilter> filters) {
        return logRepository
            .findAsync(tenantService.resolveTenant(), buildExecutionFilters(executionId, filters))
            .collectList()
            .block();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/download", produces = MediaType.TEXT_PLAIN)
    @Operation(tags = { "Logs" }, summary = "Download logs for a specific execution, taskrun or task")
    public HttpResponse<StreamedFile> downloadLogsFromExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "Filters") @Nullable @QueryFilterFormat(Resource.LOG) List<QueryFilter> filters) {
        List<LogEntry> logs = logRepository
            .findAsync(tenantService.resolveTenant(), buildExecutionFilters(executionId, filters))
            .collectList()
            .block();
        InputStream inputStream = new java.io.ByteArrayInputStream(
            (logs == null ? List.<LogEntry>of() : logs).stream()
                .map(LogEntry::toPrettyString)
                .collect(java.util.stream.Collectors.joining("\n"))
                .getBytes()
        );

        MutableHttpResponse<StreamedFile> response = HttpResponse.ok(new StreamedFile(inputStream, MediaType.TEXT_PLAIN_TYPE).attach(executionId + ".log"));
        if (!executionService.getExecution(tenantService.resolveTenant(), executionId, false).getState().getCurrent().isTerminated()) {
            return response.header(HttpHeaders.CACHE_CONTROL, "no-cache");
        }

        return response;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{executionId}/follow", produces = MediaType.TEXT_EVENT_STREAM)
    @Operation(tags = { "Logs" }, summary = "Follow logs for a specific execution")
    public Flux<Event<FollowLogEvent>> followLogsFromExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "Filters") @Nullable @QueryFilterFormat(Resource.LOG) List<QueryFilter> filters) {
        String subscriberId = UUID.randomUUID().toString();
        List<QueryFilter> effectiveFilters = buildExecutionFilters(executionId, filters);

        return Flux.<Event<FollowLogEvent>> create(emitter ->
        {
            // send a first "empty" event so the SSE is correctly initialized in the frontend in case there are no logs
            emitter.next(Event.of(FollowLogEvent.from(LogEntry.builder().build())).id("start"));

            // fetch repository first
            logRepository.findAsync(tenantService.resolveTenant(), effectiveFilters)
                .toStream()
                .forEach(logEntry -> emitter.next(Event.of(FollowLogEvent.from(logEntry)).id("progress")));

            // consume in realtime — pass the same filter list, the streaming service uses
            // the FollowLogEventMatcher (backed by Searchable<FollowLogEvent>) to apply it
            logStreamingService.registerSubscriber(executionId, subscriberId, emitter, effectiveFilters);
        }, FluxSink.OverflowStrategy.BUFFER)
            .timeout(Duration.ofHours(1)) // avoid idle SSE sockets by setting a between-item timeout
            .doFinally(ignored -> logStreamingService.unregisterSubscriber(executionId, subscriberId));
    }

    /**
     * Build the QueryFilter list for the per-execution endpoints. Purely additive: the
     * path-variable {@code executionId} is always appended as an EQUALS filter, and any
     * user-supplied filters (including a user-supplied {@code executionId} filter) are kept
     * verbatim. The combined filter set is AND-ed by the repository, so a conflicting user
     * filter just narrows the result to nothing — the URL path stays authoritative.
     */
    private static List<QueryFilter> buildExecutionFilters(String executionId, List<QueryFilter> userFilters) {
        List<QueryFilter> merged = new java.util.ArrayList<>();
        if (userFilters != null) {
            merged.addAll(userFilters);
        }
        merged.add(QueryFilter.builder()
            .field(QueryFilter.Field.EXECUTION_ID)
            .operation(QueryFilter.Op.EQUALS)
            .value(executionId)
            .build());
        return merged;
    }


    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/{executionId}")
    @Operation(tags = { "Logs" }, summary = "Delete logs for a specific execution, taskrun or task")
    public void deleteLogsFromExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The min log level filter") @Nullable @QueryValue Level minLevel,
        @Parameter(description = "The taskrun id") @Nullable @QueryValue String taskRunId,
        @Parameter(description = "The task id") @Nullable @QueryValue String taskId,
        @Parameter(description = "The attempt number") @Nullable @QueryValue Integer attempt) {
        logRepository.deleteByQuery(tenantService.resolveTenant(), executionId, taskId, taskRunId, minLevel, attempt);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/{namespace}/{flowId}")
    @Operation(tags = { "Logs" }, summary = "Delete logs for a specific execution, taskrun or task")
    public void deleteLogsFromFlow(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The flow identifier") @PathVariable String flowId,
        @Parameter(description = "The trigger id") @Nullable @QueryValue String triggerId) {
        logRepository.deleteByQuery(tenantService.resolveTenant(), namespace, flowId, triggerId);
    }
}
