package io.kestra.jdbc.runner;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.NoTransactionContext;
import io.kestra.core.runners.WorkerInstance;
import io.kestra.core.runners.WorkerTaskData;
import io.kestra.core.runners.WorkerTaskRunning;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.plugin.core.log.Log;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class JdbcWorkerJobRunningStateStoreTest {

    @Inject
    private AbstractJdbcWorkerJobRunningStateStore workerJobRunningStateStore;

    @Inject
    private JooqDSLContextWrapper dslContextWrapper;

    @Inject
    private JdbcTestUtils jdbcTestUtils;

    @BeforeAll
    void initSchema() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    @AfterEach
    void tearDown() {
        workerJobRunningStateStore.findAll().forEach(it -> workerJobRunningStateStore.deleteByKey(it.uid()));
    }

    @Test
    void shouldSaveAndDeleteWorkerJobRunning() {
        // Given
        WorkerTaskRunning workerTaskRunning = workerTaskRunning();

        // When
        workerJobRunningStateStore.save(NoTransactionContext.INSTANCE, workerTaskRunning);

        // Then
        assertThat(existsByKey(workerTaskRunning.uid())).isTrue();

        // When
        workerJobRunningStateStore.deleteByKey(workerTaskRunning.uid());

        // Then
        assertThat(existsByKey(workerTaskRunning.uid())).isFalse();
    }

    @Test
    void shouldCommitSaveBeforeReturningWhenCallerTransactionIsOpen() {
        // Given a save() performed while a caller-owned transaction is open on the
        // current thread — as happens when the worker-controller dispatches jobs from
        // inside the dispatch-queue poll transaction.
        WorkerTaskRunning workerTaskRunning = workerTaskRunning();
        AtomicBoolean visibleFromOtherConnection = new AtomicBoolean(false);

        // When
        dslContextWrapper.transaction(configuration ->
            {
                workerJobRunningStateStore.save(NoTransactionContext.INSTANCE, workerTaskRunning);

                // Then the entry must already be committed and visible from another
                // connection — otherwise the job's terminal result (processed
                // concurrently) issues a delete that misses the row and the entry
                // leaks forever. The check MUST run on another thread: on this one it
                // would join the open transaction and see the uncommitted row.
                visibleFromOtherConnection.set(
                    CompletableFuture
                        .supplyAsync(
                            () -> existsByKey(workerTaskRunning.uid()),
                            runnable -> new Thread(runnable, "other-connection").start()
                        )
                        .get(10, TimeUnit.SECONDS)
                );
            }
        );

        assertThat(visibleFromOtherConnection.get()).isTrue();
    }

    private boolean existsByKey(String key) {
        return workerJobRunningStateStore.findAll().stream().anyMatch(it -> key.equals(it.uid()));
    }

    private static WorkerTaskRunning workerTaskRunning() {
        return WorkerTaskRunning.builder()
            .workerInstance(new WorkerInstance(IdUtils.create(), null))
            .taskRun(TaskRun.builder()
                .id(IdUtils.create())
                .executionId(IdUtils.create())
                .namespace("io.kestra.unittest")
                .flowId("worker-job-running-state-store")
                .taskId("log")
                .state(new State().withState(State.Type.SUBMITTED))
                .build())
            .task(Log.builder().id("log").type(Log.class.getName()).message("test").build())
            .data(new WorkerTaskData(Map.of(), List.of(), null))
            .build();
    }
}
