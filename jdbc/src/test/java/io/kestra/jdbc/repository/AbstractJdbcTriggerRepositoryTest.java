package io.kestra.jdbc.repository;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@KestraTest
public abstract class AbstractJdbcTriggerRepositoryTest {

    @Inject
    protected AbstractJdbcTriggerRepository triggerRepository;

    /**
     * Guards against the {@code duplicate key value violates unique constraint "triggers_pkey"} error seen in
     * production when a flow holding a running (realtime) trigger is re-imported.
     * <p>
     * The trigger row is written by two paths that share the same primary key
     * ({@code tenant_namespace_flowId_triggerId}): the trigger-evaluation path persists it through an upsert
     * ({@link AbstractJdbcTriggerRepository#save}), while the scheduler initialization path
     * ({@code AbstractScheduler.initializedTriggers}) inserts it through {@link AbstractJdbcTriggerRepository#create}.
     * When the row already exists, {@code create} must be idempotent (a no-op) rather than collide on the primary key.
     * <p>
     * The production failure is a race between these two writers, but the underlying defect — a non-idempotent
     * {@code create} — is deterministic and needs no concurrency to reproduce.
     */
    @Test
    protected void shouldNotThrowWhenCreatingTriggerThatAlreadyExists() {
        // Given — a trigger row already persisted, as the trigger-evaluation/realtime path does via an upsert
        Trigger trigger = Trigger.builder()
            .tenantId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .flowId("flowId")
            .triggerId("triggerId")
            .nextExecutionDate(ZonedDateTime.now())
            .build();
        triggerRepository.save(trigger);

        // When / Then — the scheduler init path re-creates the same key; create() must ignore the existing row.
        assertDoesNotThrow(() -> triggerRepository.create(trigger));
    }

    /**
     * A polling trigger with {@code allowConcurrent} can see its evaluation result saved while the executor of an
     * earlier execution resets the same row. The reset must run under the row lock: writing back a copy read
     * earlier would put the "evaluation running" flag and the old next date back, and the trigger would never be
     * evaluated again.
     */
    @Test
    protected void resetUnderLockShouldNotOverwriteConcurrentEvaluationResult() throws Exception {
        // Given — a trigger flagged "evaluation running" by the scheduler
        ZonedDateTime evaluationStart = ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        Trigger idle = Trigger.builder()
            .tenantId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .flowId("flowId")
            .triggerId("triggerId")
            .date(evaluationStart)
            .nextExecutionDate(evaluationStart)
            .build();
        triggerRepository.save(Trigger.of(idle, evaluationStart));

        CountDownLatch resetStarted = new CountDownLatch(1);
        CountDownLatch evaluationResultSubmitted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // When — the executor resets the trigger while the evaluation result is being saved
            Future<?> reset = pool.submit(() -> triggerRepository.lock(idle.uid(), current ->
            {
                resetStarted.countDown();
                await(evaluationResultSubmitted);
                return current.resetExecution(State.Type.SUCCESS);
            }));
            resetStarted.await(10, TimeUnit.SECONDS);

            ZonedDateTime next = evaluationStart.plusSeconds(30);
            Future<?> evaluationResult = pool.submit(() -> triggerRepository.update(Trigger.of((TriggerContext) idle, next)));
            evaluationResultSubmitted.countDown();

            reset.get(10, TimeUnit.SECONDS);
            evaluationResult.get(10, TimeUnit.SECONDS);

            // Then — the evaluation result is the last word
            Trigger last = triggerRepository.findByUid(idle.uid()).orElseThrow();
            assertThat(last.getEvaluateRunningDate()).isNull();
            assertThat(last.getNextExecutionDate()).isEqualTo(next);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
