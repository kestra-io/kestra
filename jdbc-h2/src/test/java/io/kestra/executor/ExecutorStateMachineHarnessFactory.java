package io.kestra.executor;

import io.kestra.core.repositories.ConcurrencyLimitRepositoryInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.DefaultFlowMetaStore;
import io.kestra.core.utils.IdUtils;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Hands out a fresh {@link ExecutorStateMachineHarness} per test, each with its own unique tenant so
 * tests stay isolated in the shared, never-cleaned H2 file.
 */
@Singleton
@Requires(property = "kestra.test.executor-state-machine-harness", value = "true")
public class ExecutorStateMachineHarnessFactory {
    private final DefaultExecutor executor;
    private final FlowRepositoryInterface flowRepository;
    private final DefaultFlowMetaStore flowMetaStore;
    private final ExecutionRepositoryInterface executionRepository;
    private final ConcurrencyLimitRepositoryInterface concurrencyLimitRepository;
    private final QueueRecorder recorder;

    @Inject
    public ExecutorStateMachineHarnessFactory(
        DefaultExecutor executor,
        FlowRepositoryInterface flowRepository,
        DefaultFlowMetaStore flowMetaStore,
        ExecutionRepositoryInterface executionRepository,
        ConcurrencyLimitRepositoryInterface concurrencyLimitRepository,
        QueueRecorder recorder
    ) {
        this.executor = executor;
        this.flowRepository = flowRepository;
        this.flowMetaStore = flowMetaStore;
        this.executionRepository = executionRepository;
        this.concurrencyLimitRepository = concurrencyLimitRepository;
        this.recorder = recorder;
    }

    public ExecutorStateMachineHarness create() {
        return new ExecutorStateMachineHarness(
            executor,
            flowRepository,
            flowMetaStore,
            executionRepository,
            concurrencyLimitRepository,
            recorder,
            "sm-" + IdUtils.create()
        );
    }
}
