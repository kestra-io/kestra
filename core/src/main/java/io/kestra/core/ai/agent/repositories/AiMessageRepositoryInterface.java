package io.kestra.core.ai.agent.repositories;

import java.util.List;

import io.kestra.core.ai.agent.models.AgentMessage;

public interface AiMessageRepositoryInterface {
    /**
     * Appends a message to the history of the thread it belongs to.
     *
     * @param message the message to store, carrying its own {@code threadId}.
     * @return the stored message.
     */
    AgentMessage append(AgentMessage message);

    /**
     * Loads the full message history of a thread, ordered chronologically by uid. Scoped to the given
     * tenant: messages of a thread belonging to another tenant are never returned, even if the thread id
     * matches.
     *
     * @param tenant the tenant the thread belongs to.
     * @param threadId the unique identifier of the thread.
     * @return the thread's messages, or an empty list if the thread has none.
     */
    List<AgentMessage> load(String tenant, String threadId);
}
