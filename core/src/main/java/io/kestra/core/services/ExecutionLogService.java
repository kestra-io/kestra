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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

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

        List<String> levels = LogEntry.findLevelsByMin(minLevel).stream().map(Enum::name).toList();

        final AtomicReference<Runnable> disposable = new AtomicReference<>();

        return Flux.<Event<LogEntry>>create(emitter -> {
                // fetch repository first
                fetchExecutionExecutionLogs(tenantId, executionId, minLevel, levels)
                    .forEach(logEntry -> emitter.next(Event.of(logEntry).id("progress")));

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

    public Stream<LogEntry> fetchExecutionExecutionLogs(String tenantId, String executionId, Level minLevel, List<String> levels) {
        return logRepository.findByExecutionId(tenantId, executionId, minLevel, Pageable.UNPAGED)
            .stream()
            .filter(logEntry -> levels.contains(logEntry.getLevel().name()));
    }
}
