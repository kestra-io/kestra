package io.kestra.core.ai.agent.repositories;

import java.util.List;

import io.kestra.core.ai.agent.AgentMessage;

public interface AiMessageRepositoryInterface {
    /**
     * Appends a message to the history of the thread it belongs to.
     *
     * @param message the message to store, carrying its own {@code threadId}.
     * @return the stored message.
     */
    AgentMessage append(AgentMessage message);

    /**
     * Loads the full message history of a thread, ordered chronologically by uid.
     *
     * @param threadId the unique identifier of the thread.
     * @return the thread's messages, or an empty list if the thread has none.
     */
    List<AgentMessage> load(String threadId);
}
