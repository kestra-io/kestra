package io.kestra.core.ai.agent.repositories;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.ai.agent.models.AgentThreadStatus;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest(transactional = false)
public abstract class AbstractAgentThreadRepositoryTest {

    @Inject
    private ThreadStore threadStore;

    @Test
    void shouldCreateAndFindThread() {
        // Given
        String tenant = TestsUtils.randomTenant(getClass().getSimpleName());
        AgentThread thread = thread(tenant, AgentThreadStatus.IDLE);

        // When
        threadStore.create(thread);

        // Then
        assertThat(threadStore.exists(tenant, thread.uid())).isTrue();
        Optional<AgentThread> found = threadStore.find(tenant, thread.uid());
        assertThat(found).isPresent();
        assertThat(found.get().uid()).isEqualTo(thread.uid());
        assertThat(found.get().status()).isEqualTo(AgentThreadStatus.IDLE);
    }

    @Test
    void shouldNotFindThreadFromAnotherTenant() {
        // Given
        String tenant = TestsUtils.randomTenant(getClass().getSimpleName());
        AgentThread thread = thread(tenant, AgentThreadStatus.IDLE);
        threadStore.create(thread);

        // When / Then — another tenant cannot see it
        assertThat(threadStore.find("other-" + tenant, thread.uid())).isEmpty();
        assertThat(threadStore.exists("other-" + tenant, thread.uid())).isFalse();
    }

    @Test
    void shouldNotFindSoftDeletedThread() {
        // Given
        String tenant = TestsUtils.randomTenant(getClass().getSimpleName());
        AgentThread thread = thread(tenant, AgentThreadStatus.IDLE);
        threadStore.create(thread);

        // When — soft-delete via save
        threadStore.save(thread.withDeleted(true));

        // Then
        assertThat(threadStore.find(tenant, thread.uid())).isEmpty();
        assertThat(threadStore.exists(tenant, thread.uid())).isFalse();
    }

    @Test
    void shouldOverwriteOnSave() {
        // Given
        String tenant = TestsUtils.randomTenant(getClass().getSimpleName());
        AgentThread thread = thread(tenant, AgentThreadStatus.IDLE);
        threadStore.create(thread);

        // When
        threadStore.save(thread.withStatus(AgentThreadStatus.AWAITING_CONFIRMATION).withTitle("titled"));

        // Then
        AgentThread found = threadStore.find(tenant, thread.uid()).orElseThrow();
        assertThat(found.status()).isEqualTo(AgentThreadStatus.AWAITING_CONFIRMATION);
        assertThat(found.title()).isEqualTo("titled");
    }

    @Test
    void shouldApplyMutationWhenExpectedStatusMatches() {
        // Given
        String tenant = TestsUtils.randomTenant(getClass().getSimpleName());
        AgentThread thread = thread(tenant, AgentThreadStatus.IDLE);
        threadStore.create(thread);

        // When — compare-and-set from IDLE → RUNNING
        Optional<AgentThread> updated = threadStore.updateIf(
            tenant, thread.uid(), AgentThreadStatus.IDLE,
            t -> t.withStatus(AgentThreadStatus.RUNNING).withOwnerNodeId("node-1")
        );

        // Then
        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo(AgentThreadStatus.RUNNING);
        assertThat(threadStore.find(tenant, thread.uid()).orElseThrow().status()).isEqualTo(AgentThreadStatus.RUNNING);
        assertThat(threadStore.find(tenant, thread.uid()).orElseThrow().ownerNodeId()).isEqualTo("node-1");
    }

    @Test
    void shouldNotApplyMutationWhenExpectedStatusMismatches() {
        // Given — the thread is IDLE, but we require RUNNING
        String tenant = TestsUtils.randomTenant(getClass().getSimpleName());
        AgentThread thread = thread(tenant, AgentThreadStatus.IDLE);
        threadStore.create(thread);

        // When
        Optional<AgentThread> updated = threadStore.updateIf(
            tenant, thread.uid(), AgentThreadStatus.RUNNING,
            t -> t.withStatus(AgentThreadStatus.AWAITING_CONFIRMATION)
        );

        // Then — nothing changed
        assertThat(updated).isEmpty();
        assertThat(threadStore.find(tenant, thread.uid()).orElseThrow().status()).isEqualTo(AgentThreadStatus.IDLE);
    }

    @Test
    void shouldReturnEmptyUpdateIfWhenThreadMissing() {
        // Given
        String tenant = TestsUtils.randomTenant(getClass().getSimpleName());

        // When / Then
        assertThat(threadStore.updateIf(tenant, IdUtils.create(), AgentThreadStatus.IDLE, t -> t)).isEmpty();
    }

    private static AgentThread thread(final String tenant, final AgentThreadStatus status) {
        Instant now = Instant.now();
        return AgentThread.builder()
            .uid(IdUtils.create())
            .tenant(tenant)
            .mode(AgentMode.EDIT)
            .status(status)
            .createdAt(now)
            .updatedAt(now)
            .deleted(false)
            .build();
    }
}
