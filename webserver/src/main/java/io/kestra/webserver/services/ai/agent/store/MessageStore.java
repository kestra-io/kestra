package io.kestra.webserver.services.ai.agent.store;

import java.util.List;

import io.kestra.webserver.services.ai.agent.domain.AgentMessage;


public interface MessageStore {
    AgentMessage append(AgentMessage message);

    List<AgentMessage> load(String threadId);
}
