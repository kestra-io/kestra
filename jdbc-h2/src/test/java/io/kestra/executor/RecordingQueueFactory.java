package io.kestra.executor;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.queue.jdbc.JdbcQueueFactory;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Replaces the executor's exit queues with recording fakes, gated so it never leaks into the runner
 * tests that share this module. Each method's generic return type is what makes Micronaut replace
 * the right typed queue (a raw {@code @MockBean} does not). Only the queues a state-machine test
 * needs to observe are replaced here; the rest stay real and are harmless (nothing consumes them).
 */
@Factory
@Requires(property = "kestra.test.executor-state-machine-harness", value = "true")
public class RecordingQueueFactory {
    @Singleton
    @Replaces(value = DispatchQueueInterface.class, factory = JdbcQueueFactory.class)
    DispatchQueueInterface<ExecutionEvent> executionEventQueue(QueueRecorder recorder) {
        return new RecordingDispatchQueue<>(ExecutionEvent.class, recorder);
    }

    @Singleton
    @Replaces(value = KeyedDispatchQueueInterface.class, factory = JdbcQueueFactory.class)
    KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue(QueueRecorder recorder) {
        return new RecordingKeyedDispatchQueue<>(WorkerJobEvent.class, recorder);
    }

    @Singleton
    @Replaces(value = DispatchQueueInterface.class, factory = JdbcQueueFactory.class)
    DispatchQueueInterface<Execution> executionQueue(QueueRecorder recorder) {
        return new RecordingDispatchQueue<>(Execution.class, recorder);
    }
}
