package io.kestra.core.services;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.sse.Event;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.event.Level;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for fetching logs for from an execution.
 */
@Singleton
public class ExecutionLogService {

    @Inject
    private LogRepositoryInterface logRepository;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    protected QueueInterface<LogEntry> logQueue;

    public Flux<Event<LogEntry>> streamExecutionLogs(final String tenantId,
                                                     final String executionId,
                                                     final Level minLevel) {

        final AtomicReference<Runnable> disposable = new AtomicReference<>();

        return Flux.<Event<LogEntry>>create(emitter -> {
                // fetch repository first
                getExecutionLogs(tenantId, executionId, minLevel, List.of())
                    .forEach(logEntry -> emitter.next(Event.of(logEntry).id("progress")));

                final List<String> levels = LogEntry.findLevelsByMin(minLevel).stream().map(Enum::name).toList();

                // consume in realtime
                disposable.set(this.logQueue.receive(either -> {
                    if (either.isRight()) {
                        return;
                    }

                    LogEntry current = either.getLeft();

                    if (current.getExecutionId() != null && current.getExecutionId().equals(executionId)) {
                        if (levels.contains(current.getLevel().name())) {
                            emitter.next(Event.of(current).id("progress"));
                        }
                    }
                }));
            }, FluxSink.OverflowStrategy.BUFFER)
            .doOnCancel(() -> {
                if (disposable.get() != null) {
                    disposable.get().run();
                }
            })
            .doOnComplete(() -> {
                if (disposable.get() != null) {
                    disposable.get().run();
                }
            });
    }

    public InputStream getExecutionLogsAsStream(final String tenantId,
                                                final String executionId,
                                                final Level minLevel,
                                                final String taskRunId,
                                                final List<String> taskIds,
                                                final Integer attempt) {
        List<LogEntry> logs = getExecutionLogs(tenantId, executionId, minLevel, taskRunId, taskIds, attempt);
        return new ByteArrayInputStream(logs.stream().map(LogEntry::toPrettyString).collect(Collectors.joining("\n")).getBytes());
    }

    public List<LogEntry> getExecutionLogs(final String tenantId,
                                           final String executionId,
                                           final Level minLevel,
                                           final String taskRunId,
                                           final List<String> taskIds,
                                           final Integer attempt) {
        if (taskIds != null) {
            return taskIds.size() == 1 ?
                logRepository.findByExecutionIdAndTaskId(tenantId, executionId, taskIds.getFirst(), minLevel):
                getExecutionLogs(tenantId, executionId, minLevel, taskIds).toList();
        }

        if (taskRunId != null) {
            return attempt != null ?
                logRepository.findByExecutionIdAndTaskRunIdAndAttempt(tenantId, executionId, taskRunId, minLevel, attempt) :
                logRepository.findByExecutionIdAndTaskRunId(tenantId, executionId, taskRunId, minLevel);
        }
        return logRepository.findByExecutionId(tenantId, executionId, minLevel);
    }

    public Stream<LogEntry> getExecutionLogs(String tenantId,
                                             String executionId,
                                             Level minLevel,
                                             List<String> taskIds) {
        return logRepository.findByExecutionId(tenantId, executionId, minLevel)
            .stream()
            .filter(data -> taskIds.isEmpty() || taskIds.contains(data.getTaskId()));
    }
}
