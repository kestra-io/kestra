package io.kestra.executor;

import jakarta.inject.Inject;

/**
 * Base for executor state-machine tests: real H2 stores, real {@link DefaultExecutor}, recording
 * queues, no runner/worker/scheduler. Concrete tests are annotated {@link ExecutorStateMachineTest},
 * which boots the context and enables the gated recording-queue factory (kept out of every other
 * context in this module so the runner tests are unaffected).
 *
 * <p>Each test creates its own harness with {@code harnesses.create()} and keeps it in a local
 * variable — no shared field — so tests read straight through and stay parallel-safe.
 */
public abstract class AbstractExecutorStateMachineTest {
    @Inject
    protected ExecutorStateMachineHarnessFactory harnesses;
}
