package io.kestra.core.services;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.micronaut.http.sse.Event;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.event.Level;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

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
                                                     final Level minLevel,
                                                     final boolean withAccessControl) {

        final AtomicReference<Runnable> disposable = new AtomicReference<>();

        return Flux.<Event<LogEntry>>create(emitter -> {

                // send a first "empty" event so the SSE is correctly initialized in the frontend in case there are no logs
                emitter.next(Event.of(LogEntry.builder().build()).id("start"));

                // fetch repository first
                getExecutionLogs(tenantId, executionId, minLevel, List.of(), withAccessControl)
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
            .doFinally(ignored -> {
                Schedulers.boundedElastic().schedule(() -> {
                    if (disposable.get() != null) {
                        disposable.get().run();
                    }
                });
            });
    }

    public InputStream getExecutionLogsAsStream(String tenantId,
                                                String executionId,
                                                Level minLevel,
                                                String taskRunId,
                                                List<String> taskIds,
                                                Integer attempt,
                                                boolean withAccessControl) {
        List<LogEntry> logs = getExecutionLogs(tenantId, executionId, minLevel, taskRunId, taskIds, attempt, withAccessControl);
        return new ByteArrayInputStream(logs.stream().map(LogEntry::toPrettyString).collect(Collectors.joining("\n")).getBytes());
    }

    public List<LogEntry> getExecutionLogs(String tenantId,
                                           String executionId,
                                           Level minLevel,
                                           String taskRunId,
                                           List<String> taskIds,
                                           Integer attempt,
                                           boolean withAccessControl) {
        // Get by Execution ID and TaskID.
        if (taskIds != null) {
            if (taskIds.size() == 1) {
                return withAccessControl ?
                    logRepository.findByExecutionIdAndTaskId(tenantId, executionId, taskIds.getFirst(), minLevel) :
                    logRepository.findByExecutionIdAndTaskIdWithoutAcl(tenantId, executionId, taskIds.getFirst(), minLevel);
            } else {
                return getExecutionLogs(tenantId, executionId, minLevel, taskIds, withAccessControl).toList();
            }
        }

        // Get by Execution ID, TaskRunID, and attempt.
        if (taskRunId != null) {
            if (attempt != null) {
                return withAccessControl ?
                    logRepository.findByExecutionIdAndTaskRunIdAndAttempt(tenantId, executionId, taskRunId, minLevel, attempt) :
                    logRepository.findByExecutionIdAndTaskRunIdAndAttemptWithoutAcl(tenantId, executionId, taskRunId, minLevel, attempt);
            } else {
                return withAccessControl ?
                    logRepository.findByExecutionIdAndTaskRunId(tenantId, executionId, taskRunId, minLevel) :
                    logRepository.findByExecutionIdAndTaskRunIdWithoutAcl(tenantId, executionId, taskRunId, minLevel);
            }
        }

        // Get by Execution ID
        return withAccessControl ?
             logRepository.findByExecutionId(tenantId, executionId, minLevel) :
             logRepository.findByExecutionIdWithoutAcl(tenantId, executionId, minLevel);
    }

    public Stream<LogEntry> getExecutionLogs(String tenantId,
                                             String executionId,
                                             Level minLevel,
                                             List<String> taskIds,
                                             boolean withAccessControl) {

        List<LogEntry> results = withAccessControl ?
            logRepository.findByExecutionId(tenantId, executionId, minLevel) :
            logRepository.findByExecutionIdWithoutAcl(tenantId, executionId, minLevel);

        return results
            .stream()
            .filter(data -> taskIds.isEmpty() || taskIds.contains(data.getTaskId()));
    }
}
