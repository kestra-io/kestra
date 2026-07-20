package io.kestra.core.ai.agent.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.ai.agent.models.AgentMode;
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
    private AiThreadRepositoryInterface threadStore;

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
    void shouldListThreadsForOwningUserOnly() {
        // Given — two threads for user-1, one for user-2, and one soft-deleted for user-1
        String tenant = TestsUtils.randomTenant(getClass().getSimpleName());
        AgentThread mine1 = thread(tenant, "user-1", AgentThreadStatus.IDLE);
        AgentThread mine2 = thread(tenant, "user-1", AgentThreadStatus.IDLE);
        AgentThread theirs = thread(tenant, "user-2", AgentThreadStatus.IDLE);
        AgentThread deleted = thread(tenant, "user-1", AgentThreadStatus.IDLE);
        threadStore.create(mine1);
        threadStore.create(mine2);
        threadStore.create(theirs);
        threadStore.create(deleted);
        threadStore.delete(deleted);

        // When
        List<AgentThread> listed = threadStore.findAllForUser(tenant, "user-1");

        // Then — only the caller's non-deleted threads, never another user's or a deleted one
        assertThat(listed).extracting(AgentThread::uid)
            .containsExactlyInAnyOrder(mine1.uid(), mine2.uid());
    }

    @Test
    void shouldNotListThreadsFromAnotherTenant() {
        // Given
        String tenant = TestsUtils.randomTenant(getClass().getSimpleName());
        AgentThread thread = thread(tenant, "user-1", AgentThreadStatus.IDLE);
        threadStore.create(thread);

        // When / Then — the same user in a different tenant sees nothing
        assertThat(threadStore.findAllForUser("other-" + tenant, "user-1")).isEmpty();
    }

    @Test
    void shouldSoftDeleteThread() {
        // Given
        String tenant = TestsUtils.randomTenant(getClass().getSimpleName());
        AgentThread thread = thread(tenant, "user-1", AgentThreadStatus.IDLE);
        threadStore.create(thread);

        // When
        AgentThread deleted = threadStore.delete(thread);

        // Then — soft-deleted: flagged, and invisible to every lookup
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(threadStore.find(tenant, thread.uid())).isEmpty();
        assertThat(threadStore.exists(tenant, thread.uid())).isFalse();
        assertThat(threadStore.findAllForUser(tenant, "user-1")).isEmpty();
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
        return thread(tenant, null, status);
    }

    private static AgentThread thread(final String tenant, final String userId, final AgentThreadStatus status) {
        Instant now = Instant.now();
        return AgentThread.builder()
            .uid(IdUtils.create())
            .tenant(tenant)
            .userId(userId)
            .mode(AgentMode.EDIT)
            .status(status)
            .createdAt(now)
            .updatedAt(now)
            .deleted(false)
            .build();
    }
}
