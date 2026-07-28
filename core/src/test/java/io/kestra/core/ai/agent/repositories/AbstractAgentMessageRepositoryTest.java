package io.kestra.core.ai.agent.repositories;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.ai.agent.models.AgentMessageRole;
import io.kestra.core.ai.agent.models.AgentMessageType;
import io.kestra.core.utils.IdUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest(transactional = false)
public abstract class AbstractAgentMessageRepositoryTest {

    @Inject
    private AiMessageRepositoryInterface messageStore;

    @Test
    void shouldAppendAndLoadInKeyOrder() {
        // Given — a thread with three messages appended out of key order
        String tenant = IdUtils.create();
        String threadId = IdUtils.create();
        messageStore.append(message(tenant, threadId, "002", "second"));
        messageStore.append(message(tenant, threadId, "001", "first"));
        messageStore.append(message(tenant, threadId, "003", "third"));

        // When
        List<AgentMessage> loaded = messageStore.load(tenant, threadId);

        // Then — chronological order by the monotonic uid/key
        assertThat(loaded).extracting(AgentMessage::content).containsExactly("first", "second", "third");
    }

    @Test
    void shouldScopeMessagesToTheirThread() {
        // Given — two threads
        String tenant = IdUtils.create();
        String threadA = IdUtils.create();
        String threadB = IdUtils.create();
        messageStore.append(message(tenant, threadA, threadA + "-1", "a1"));
        messageStore.append(message(tenant, threadB, threadB + "-1", "b1"));

        // When / Then — each thread only sees its own messages
        assertThat(messageStore.load(tenant, threadA)).extracting(AgentMessage::content).containsExactly("a1");
        assertThat(messageStore.load(tenant, threadB)).extracting(AgentMessage::content).containsExactly("b1");
    }

    @Test
    void shouldScopeMessagesToTheirTenant() {
        // Given — a thread whose messages belong to one tenant
        String tenant = IdUtils.create();
        String otherTenant = IdUtils.create();
        String threadId = IdUtils.create();
        messageStore.append(message(tenant, threadId, threadId + "-1", "a1"));

        // When / Then — loading the same thread id under another tenant leaks nothing
        assertThat(messageStore.load(tenant, threadId)).extracting(AgentMessage::content).containsExactly("a1");
        assertThat(messageStore.load(otherTenant, threadId)).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUnknownThread() {
        assertThat(messageStore.load(IdUtils.create(), IdUtils.create())).isEmpty();
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
