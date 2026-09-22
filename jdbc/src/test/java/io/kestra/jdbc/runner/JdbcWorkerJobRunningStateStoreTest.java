package io.kestra.jdbc.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.NoTransactionContext;
import io.kestra.core.runners.WorkerInstance;
import io.kestra.core.runners.WorkerJobRunning;
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

    private static final Field<Object> KEY_FIELD = DSL.field(DSL.quotedName("key"));
    private static final Field<Object> VALUE_FIELD = DSL.field(DSL.quotedName("value"));

    private static final String LEGACY_WORKER_UID = "60172c81-93fb-4ad4-b835-4792787b6e92";

    /** A running entry as a 1.x worker wrote it: the payload predates the move to {@code data}. */
    private static final String LEGACY_TRIGGER_JSON = """
        {
          "type": "trigger",
          "partition": 0,
          "workerInstance": {"uid": "%s", "workerGroup": null},
          "trigger": {"id": "watch_http", "type": "io.kestra.plugin.core.http.Trigger", "uri": "http://localhost:28080/"},
          "triggerContext": {"tenantId": "main", "namespace": "company.tmc", "flowId": "legacy_repro", "triggerId": "watch_http"},
          "conditionContext": {}
        }
        """.formatted(LEGACY_WORKER_UID);

    @Inject
    private AbstractJdbcWorkerJobRunningStateStore workerJobRunningStateStore;

    @Inject
    private JooqDSLContextWrapper dslContextWrapper;

    @Inject
    private JdbcTestUtils jdbcTestUtils;

    @BeforeAll
    @SuppressWarnings("deprecation")
    void initSchema() {
        jdbcTestUtils.drop();
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

    @Test
    void shouldDiscardEntryWrittenByPreviousVersionWhenProcessingDeadWorker() {
        // Given a worker holding one entry written by a previous version alongside a current one.
        // The legacy entry carries none of the fields a consumer reads, and letting it through aborts
        // the executor's whole liveness sweep — including the vNode rebalance that follows it.
        insertRawEntry("legacy-trigger-entry", LEGACY_TRIGGER_JSON);

        WorkerTaskRunning current = workerTaskRunning(LEGACY_WORKER_UID);
        workerJobRunningStateStore.save(NoTransactionContext.INSTANCE, current);

        // When
        List<WorkerJobRunning> consumed = new ArrayList<>();
        workerJobRunningStateStore.processWorkerJobsForDeadWorker(
            NoTransactionContext.INSTANCE,
            LEGACY_WORKER_UID,
            (txContext, workerJobRunning) -> consumed.add(workerJobRunning)
        );

        // Then the consumer only sees what it can act on, and the stale lease is gone for good.
        assertThat(consumed).hasSize(1);
        assertThat(consumed.getFirst().uid()).isEqualTo(current.uid());
        assertThat(rawKeys()).containsExactly(current.uid());
    }

    private void insertRawEntry(String key, String json) {
        dslContextWrapper.transaction(
            configuration -> DSL.using(configuration)
                .insertInto(DSL.table("worker_job_running"))
                .set(KEY_FIELD, (Object) key)
                .set(VALUE_FIELD, (Object) JSONB.valueOf(json))
                .execute()
        );
    }

    private List<String> rawKeys() {
        return dslContextWrapper.transactionResult(
            configuration -> DSL.using(configuration)
                .select(KEY_FIELD)
                .from(DSL.table("worker_job_running"))
                .fetch()
                .map(record -> record.get(KEY_FIELD, String.class))
        );
    }

    private boolean existsByKey(String key) {
        return workerJobRunningStateStore.findAll().stream().anyMatch(it -> key.equals(it.uid()));
    }

    private static WorkerTaskRunning workerTaskRunning() {
        return workerTaskRunning(IdUtils.create());
    }

    private static WorkerTaskRunning workerTaskRunning(String workerUid) {
        return WorkerTaskRunning.builder()
            .workerInstance(new WorkerInstance(workerUid, null))
            .taskRun(
                TaskRun.builder()
                    .id(IdUtils.create())
                    .executionId(IdUtils.create())
                    .namespace("io.kestra.unittest")
                    .flowId("worker-job-running-state-store")
                    .taskId("log")
                    .state(new State().withState(State.Type.SUBMITTED))
                    .build()
            )
            .task(Log.builder().id("log").type(Log.class.getName()).message("test").build())
            .data(new WorkerTaskData(Map.of(), null))
            .build();
    }
}
