package io.kestra.jdbc.repository;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

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
}
