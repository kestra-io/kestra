package io.kestra.webserver.controllers;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.format.Format;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.sse.Event;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.event.Level;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Validated
@Controller("/api/v1/")
@Requires(beans = LogRepositoryInterface.class)
public class LogController {
    @Inject
    private LogRepositoryInterface logRepository;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    protected QueueInterface<LogEntry> logQueue;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "logs/search", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Logs"}, summary = "Search for logs")
    public PagedResults<LogEntry> find(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A flow id filter") @Nullable @QueryValue String flowId,
        @Parameter(description = "The min log level filter") @Nullable @QueryValue Level minLevel,
        @Parameter(description = "The start datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime startDate,
        @Parameter(description = "The end datetime") @Nullable @Format("yyyy-MM-dd'T'HH:mm[:ss][.SSS][XXX]") @QueryValue ZonedDateTime endDate
    ) {
        return PagedResults.of(
            logRepository.find(PageableUtils.from(page, size, sort), query, namespace, flowId, minLevel, startDate, endDate)
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "logs/{executionId}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Logs"}, summary = "Get logs for a specific execution, taskrun or task")
    public List<LogEntry> findByExecution(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The min log level filter") @Nullable @QueryValue Level minLevel,
        @Parameter(description = "The taskrun id") @Nullable @QueryValue String taskRunId,
        @Parameter(description = "The task id") @Nullable @QueryValue String taskId,
        @Parameter(description = "The attempt number") @Nullable @QueryValue Integer attempt
    ) {
        if (taskId != null) {
            return logRepository.findByExecutionIdAndTaskId(executionId, taskId, minLevel);
        } else if (taskRunId != null) {
            if (attempt != null) {
                return logRepository.findByExecutionIdAndTaskRunIdAndAttempt(executionId, taskRunId, minLevel, attempt);
            }
            return logRepository.findByExecutionIdAndTaskRunId(executionId, taskRunId, minLevel);
        } else {
            return logRepository.findByExecutionId(executionId, minLevel);
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "logs/{executionId}/download", produces = MediaType.TEXT_PLAIN)
    @Operation(tags = {"Logs"}, summary = "Download logs for a specific execution, taskrun or task")
    public StreamedFile download(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The min log level filter") @Nullable @QueryValue Level minLevel,
        @Parameter(description = "The taskrun id") @Nullable @QueryValue String taskRunId,
        @Parameter(description = "The task id") @Nullable @QueryValue String taskId,
        @Parameter(description = "The attempt number") @Nullable @QueryValue Integer attempt
    ) {
        List<LogEntry> logEntries;
        if (taskId != null) {
            logEntries = logRepository.findByExecutionIdAndTaskId(executionId, taskId, minLevel);
        } else if (taskRunId != null) {
            if (attempt != null) {
                logEntries = logRepository.findByExecutionIdAndTaskRunIdAndAttempt(executionId, taskRunId, minLevel, attempt);
            } else {
                logEntries = logRepository.findByExecutionIdAndTaskRunId(executionId, taskRunId, minLevel);
            }
        } else {
            logEntries = logRepository.findByExecutionId(executionId, minLevel);
        }
        InputStream inputStream = new ByteArrayInputStream(logEntries.stream().map(LogEntry::toPrettyString).collect(Collectors.joining("\n")).getBytes());
        return new StreamedFile(inputStream, MediaType.TEXT_PLAIN_TYPE).attach(executionId + ".log");
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "logs/{executionId}/follow", produces = MediaType.TEXT_EVENT_STREAM)
    @Operation(tags = {"Logs"}, summary = "Follow logs for a specific execution")
    public Flowable<Event<LogEntry>> follow(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The min log level filter") @Nullable @QueryValue Level minLevel
    ) {
        AtomicReference<Runnable> cancel = new AtomicReference<>();
        List<String> levels = LogEntry.findLevelsByMin(minLevel);

        return Flowable
            .<Event<LogEntry>>create(emitter -> {
                // fetch repository first
                logRepository.findByExecutionId(executionId, minLevel, null)
                    .stream()
                    .filter(logEntry -> levels.contains(logEntry.getLevel().name()))
                    .forEach(logEntry -> emitter.onNext(Event.of(logEntry).id("progress")));

                // consume in realtime
                Runnable receive = this.logQueue.receive(either -> {
                    if (either.isRight()) {
                        return;
                    }

                    LogEntry current = either.getLeft();
                    if (current.getExecutionId() != null && current.getExecutionId().equals(executionId)) {
                        if (levels.contains(current.getLevel().name())) {
                            emitter.onNext(Event.of(current).id("progress"));
                        }
                    }
                });

                cancel.set(receive);
            }, BackpressureStrategy.BUFFER)
            .doOnCancel(() -> {
                if (cancel.get() != null) {
                    cancel.get().run();
                }
            })
            .doOnComplete(() -> {
                if (cancel.get() != null) {
                    cancel.get().run();
                }
            });
    }
}
