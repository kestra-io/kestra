package io.kestra.webserver.services.ai.agent.store;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.kestra.webserver.services.ai.agent.domain.Message;

import jakarta.inject.Singleton;

@Singleton
public class InMemoryMessageStore implements MessageStore {
    private final Map<String, List<Message>> messagesByThread = new ConcurrentHashMap<>();

    @Override
    public Message append(final Message message) {
        Objects.requireNonNull(message, "message");
        messagesByThread
            .computeIfAbsent(message.threadId(), ignored -> new CopyOnWriteArrayList<>())
            .add(message);
        return message;
    }

    @Override
    public List<Message> load(final String threadId) {
        return messagesByThread.getOrDefault(threadId, List.of()).stream()
            .sorted(Comparator.comparing(Message::uid))
            .toList();
    }
}
