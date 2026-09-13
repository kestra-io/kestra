package io.kestra.webserver.services.ai.agent.store;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.ai.agent.models.AgentMessageRole;
import io.kestra.core.ai.agent.models.AgentMessageType;
import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.ai.agent.models.AgentThreadStatus;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.agent.AgentConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAgentStoreTest {

    private static final String TENANT = "tenant-a";

    private static InMemoryAgentStore store(final Duration ttl, final int cap) {
        // InMemoryAgentStore reads only these two fields; the rest are left at their builder defaults so
        // adding a new AgentConfiguration field never touches this test.
        return new InMemoryAgentStore(
            AgentConfiguration.builder()
                .inMemoryConversationTtl(ttl)
                .maxInMemoryConversations(cap)
                .build()
        );
    }

    private static InMemoryAgentStore store() {
        return store(Duration.ofHours(1), 50);
    }

    @Test
    void shouldCreateFindAndScopeThreadByTenant() {
        InMemoryAgentStore store = store();
        AgentThread thread = thread(TENANT, null, AgentThreadStatus.IDLE);
        store.create(thread);

        assertThat(store.find(TENANT, thread.uid())).map(AgentThread::uid).contains(thread.uid());
        assertThat(store.exists(TENANT, thread.uid())).isTrue();
        assertThat(store.find("other", thread.uid())).isEmpty();
        assertThat(store.exists("other", thread.uid())).isFalse();
    }

    @Test
    void shouldSoftDeleteThread() {
        InMemoryAgentStore store = store();
        AgentThread thread = thread(TENANT, null, AgentThreadStatus.IDLE);
        store.create(thread);

        AgentThread deleted = store.delete(thread);

        assertThat(deleted.isDeleted()).isTrue();
        assertThat(store.find(TENANT, thread.uid())).isEmpty();
    }

    @Test
    void shouldNeverListConversationsInOss() {
        // OSS exposes no browsable history: findAllForUser is always empty even with live conversations.
        InMemoryAgentStore store = store();
        InMemoryAiThreadRepository repository = new InMemoryAiThreadRepository(store);
        store.create(thread(TENANT, "user-1", AgentThreadStatus.IDLE));
        store.create(thread(TENANT, "user-1", AgentThreadStatus.IDLE));

        assertThat(repository.findAllForUser(TENANT, "user-1")).isEmpty();
    }

    @Test
    void shouldApplyUpdateIfOnlyWhenStatusMatches() {
        InMemoryAgentStore store = store();
        AgentThread thread = thread(TENANT, null, AgentThreadStatus.IDLE);
        store.create(thread);

        assertThat(store.updateIf(TENANT, thread.uid(), AgentThreadStatus.RUNNING, t -> t.withStatus(AgentThreadStatus.AWAITING_CONFIRMATION))).isEmpty();

        Optional<AgentThread> updated = store.updateIf(TENANT, thread.uid(), AgentThreadStatus.IDLE, t -> t.withStatus(AgentThreadStatus.RUNNING));
        assertThat(updated).map(AgentThread::status).contains(AgentThreadStatus.RUNNING);
        assertThat(store.updateIf(TENANT, IdUtils.create(), AgentThreadStatus.IDLE, t -> t)).isEmpty();
    }

    @Test
    void shouldAppendAndLoadMessagesInUidOrderScopedByTenant() {
        InMemoryAgentStore store = store();
        String threadId = IdUtils.create();
        store.append(message(TENANT, threadId, "002", "second"));
        store.append(message(TENANT, threadId, "001", "first"));
        store.append(message("other", threadId, "003", "leaked?"));

        assertThat(store.load(TENANT, threadId)).extracting(AgentMessage::content).containsExactly("first", "second");
        assertThat(store.load("other", threadId)).extracting(AgentMessage::content).containsExactly("leaked?");
    }

    @Test
    void shouldEvictOldestPastTheSizeCap() throws InterruptedException {
        InMemoryAgentStore store = store(Duration.ofHours(1), 2);
        AgentThread t1 = thread(TENANT, null, AgentThreadStatus.IDLE);
        AgentThread t2 = thread(TENANT, null, AgentThreadStatus.IDLE);
        AgentThread t3 = thread(TENANT, null, AgentThreadStatus.IDLE);
        store.create(t1);
        Thread.sleep(2);
        store.create(t2);
        Thread.sleep(2);
        store.create(t3);

        // Over the cap of 2, the least-recently-active (t1) is evicted; the two newest survive.
        assertThat(store.find(TENANT, t1.uid())).isEmpty();
        assertThat(store.find(TENANT, t2.uid())).isPresent();
        assertThat(store.find(TENANT, t3.uid())).isPresent();
    }

    @Test
    void shouldEvictConversationsIdlePastTheTtl() throws InterruptedException {
        InMemoryAgentStore store = store(Duration.ofMillis(1), 500);
        AgentThread stale = thread(TENANT, null, AgentThreadStatus.IDLE);
        store.create(stale);
        Thread.sleep(10);

        // Any subsequent write triggers the sweep; the idle conversation is gone.
        store.create(thread(TENANT, null, AgentThreadStatus.IDLE));
        assertThat(store.find(TENANT, stale.uid())).isEmpty();
    }

    private static AgentThread thread(final String tenant, final String userId, final AgentThreadStatus status) {
        Instant now = Instant.now();
        return AgentThread.builder()
            .uid(IdUtils.create())
            .tenant(tenant)
            .userId(userId)
            .mode(AgentMode.ASK)
            .status(status)
            .createdAt(now)
            .updatedAt(now)
            .deleted(false)
            .build();
    }

    private static AgentMessage message(final String tenant, final String threadId, final String uid, final String content) {
        return AgentMessage.builder()
            .uid(uid)
            .tenant(tenant)
            .threadId(threadId)
            .role(AgentMessageRole.USER)
            .type(AgentMessageType.TEXT)
            .content(content)
            .traceId(IdUtils.create())
            .createdAt(Instant.now())
            .build();
    }
}
