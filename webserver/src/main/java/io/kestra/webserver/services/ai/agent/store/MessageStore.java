package io.kestra.webserver.services.ai.agent.store;

import java.util.List;

import io.kestra.webserver.services.ai.agent.domain.Message;


public interface MessageStore {
    Message append(Message message);

    List<Message> load(String threadId);
}
