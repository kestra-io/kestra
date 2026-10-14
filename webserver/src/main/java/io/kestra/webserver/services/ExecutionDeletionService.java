package io.kestra.webserver.services;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import io.kestra.core.async.AsyncOperation;
import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.async.AsyncOperationService;
import io.kestra.core.events.Actor;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.utils.Rethrow;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ExecutionDeletionService {
    private static final int MIN_DELETION_THREADS = 4;

    private final AsyncOperationService asyncOperationService;
    private final ExecutorService deletionExecutorService;

    @Inject
    public ExecutionDeletionService(AsyncOperationService asyncOperationService, ExecutorsUtils executorsUtils) {
        this.asyncOperationService = Objects.requireNonNull(asyncOperationService);
        int maxAsyncThreads = Math.max(MIN_DELETION_THREADS, executorsUtils.getAllocatedCpuCores());
        this.deletionExecutorService = executorsUtils.maxCachedVirtualThreadPool(maxAsyncThreads, "execution-deletion");
    }

    @PreDestroy
    void shutdownDeletionExecutor() {
        ExecutorsUtils.closeExecutorService("execution-deletion", deletionExecutorService, Duration.ofSeconds(10));
    }

    /**
     * Deletes the execution on a background thread, then reports the outcome under the given operation id
     * so the bulk-delete notification can track progress.
     * The actor is propagated to the background thread so deletion events keep their author.
     */
    public void deleteAsync(Execution execution, String operationId, Actor actor, Rethrow.RunnableChecked<Exception> deletion) {
        deletionExecutorService.submit(() -> {
            AsyncOperationProcessedEvent.Outcome outcome = AsyncOperationProcessedEvent.Outcome.SUCCEEDED;
            String error = null;
            try (var ignored = Actor.propagate(actor)) {
                deletion.run();
            } catch (Exception e) {
                outcome = AsyncOperationProcessedEvent.Outcome.FAILED;
                error = e.getMessage();
                log.error("Failed to delete execution {}", execution.getId(), e);
            } finally {
                asyncOperationService.emitProcessedIfAsync(
                    new DeleteOperation(operationId), execution.getTenantId(), execution.getId(), outcome, error
                );
            }
        });
    }

    private record DeleteOperation(String operationId) implements AsyncOperation {
    }
}
