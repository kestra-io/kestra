package io.kestra.jdbc;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.WorkerTriggerResult;
import io.kestra.core.utils.Either;
import io.kestra.executor.WorkerJobRunningStateStore;
import io.kestra.jdbc.runner.JdbcQueue;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class JdbcWorkerTriggerResultQueueService implements Closeable {
    private final static ObjectMapper MAPPER = JdbcMapper.of();

    @Inject
    private WorkerJobRunningStateStore workerJobRunningStateStore;

    private final AtomicReference<Runnable> disposable = new AtomicReference<>();

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    public Runnable receive(JdbcQueue<WorkerTriggerResult> workerTriggerResultQueue, String consumerGroup, Class<?> queueType,
        Consumer<Either<WorkerTriggerResult, DeserializationException>> consumer) {
        disposable.set(workerTriggerResultQueue.receiveTransaction(consumerGroup, queueType, (dslContext, eithers) ->
        {
            eithers.forEach(either ->
            {
                if (either.isRight()) {
                    log.error("Unable to deserialize a worker job: {}", either.getRight().getMessage());
                    try {
                        JsonNode json = MAPPER.readTree(either.getRight().getRecord());
                        var triggerContext = MAPPER.treeToValue(json.get("triggerContext"), TriggerContext.class);
                        workerJobRunningStateStore.deleteByKey(triggerContext.uid());
                    } catch (JsonProcessingException | DeserializationException e) {
                        // ignore the message if we cannot do anything about it
                        log.error("Unexpected exception when trying to handle a deserialization error", e);
                    }
                } else {
                    WorkerTriggerResult workerTriggerResult = either.getLeft();
                    if (isTerminalTriggerResult(workerTriggerResult)) {
                        workerJobRunningStateStore.deleteByKey(workerTriggerResult.getTriggerContext().uid());
                    }
                }
                consumer.accept(either);
            });
        }));

        return disposable.get();
    }

    /**
     * Checks whether a result means the trigger is done running on the worker, and its
     * {@link io.kestra.core.runners.WorkerJobRunning} entry can be released.
     *
     * <p>
     * A polling trigger is evaluated once, so its single result is always terminal. A realtime trigger
     * sends one result per emitted execution while it keeps running; those must not release the entry,
     * which the liveness coordinator relies on to resubmit the trigger when its worker dies. Only a
     * terminal result does — a FAILED evaluation, or an error reported without an execution.
     *
     * @param result the {@link WorkerTriggerResult} to check.
     * @return {@code true} if the entry can be released.
     */
    static boolean isTerminalTriggerResult(final WorkerTriggerResult result) {
        if (!(result.getTrigger() instanceof RealtimeTriggerInterface)) {
            return true;
        }

        return result.getExecution()
            .map(execution -> State.Type.FAILED.equals(execution.getState().getCurrent()))
            .orElse(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        if (!isClosed.compareAndSet(false, true)) {
            return;
        }

        Runnable runnable = this.disposable.get();
        if (runnable != null) {
            runnable.run();
            this.disposable.set(null);
        }
    }
}
