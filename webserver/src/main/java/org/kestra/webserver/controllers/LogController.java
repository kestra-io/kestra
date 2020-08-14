package org.kestra.webserver.controllers;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.sse.Event;
import io.micronaut.validation.Validated;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.LogRepositoryInterface;
import org.kestra.webserver.responses.PagedResults;
import org.kestra.webserver.utils.PageableUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

@Validated
@Controller("/api/v1/")
@Requires(beans = LogRepositoryInterface.class)
public class LogController {
    @Inject
    private LogRepositoryInterface logRepository;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    protected QueueInterface<LogEntry> logQueue;

    /**
     * Search for logs
     *
     * @param query The lucene query
     * @param page The current page
     * @param size The current page size
     * @param sort The sort of current page
     * @return Paged log result
     */
    @Get(uri = "logs/search", produces = MediaType.TEXT_JSON)
    public PagedResults<LogEntry> find(
        @QueryValue(value = "q") String query,
        @QueryValue(value = "page", defaultValue = "1") int page,
        @QueryValue(value = "size", defaultValue = "10") int size,
        @Nullable @QueryValue(value = "sort") List<String> sort
    ) {
        return PagedResults.of(
            logRepository.find(query, PageableUtils.from(page, size, sort))
        );
    }

    /**
     * Get execution log
     *
     * @param executionId The execution identifier
     * @param page The current page
     * @param size The current page size
     * @param sort The sort of current page
     * @return Paged log result
     */
    @Get(uri = "logs/{executionId}", produces = MediaType.TEXT_JSON)
    public PagedResults<LogEntry> findByExecution(
        String executionId,
        @QueryValue(value = "page", defaultValue = "1") int page,
        @QueryValue(value = "size", defaultValue = "10") int size,
        @Nullable @QueryValue(value = "sort") List<String> sort
    ) {
        return PagedResults.of(
            logRepository.findByExecutionId(executionId, PageableUtils.from(page, size, sort))
        );
    }

    /**
     * Get execution log for a specific taskRun
     *
     * @param executionId The execution identifier
     * @param taskRunId The taskrun identifier
     * @param page The current page
     * @param size The current page size
     * @param sort The sort of current page
     * @return Paged log result
     */
    @Get(uri = "logs/{executionId}/{taskRunId}", produces = MediaType.TEXT_JSON)
    public PagedResults<LogEntry> findByTaskrun(
        String executionId,
        String taskRunId,
        @QueryValue(value = "page", defaultValue = "1") int page,
        @QueryValue(value = "size", defaultValue = "10") int size,
        @Nullable @QueryValue(value = "sort") List<String> sort
    ) {
        return PagedResults.of(
            logRepository.findByExecutionIdAndTaskRunId(executionId, taskRunId, PageableUtils.from(page, size, sort))
        );
    }

    /**
     * Follow log for a specific execution
     *
     * @param executionId The execution id to follow
     * @return execution log sse event
     */
    @Get(uri = "logs/{executionId}/follow", produces = MediaType.TEXT_JSON)
    public Flowable<Event<LogEntry>> follow(String executionId) {
        AtomicReference<Runnable> cancel = new AtomicReference<>();

        return Flowable
            .<Event<LogEntry>>create(emitter -> {
                Runnable receive = this.logQueue.receive(current -> {
                    if (current.getExecutionId().equals(executionId)) {
                        emitter.onNext(Event.of(current).id("progress"));
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
