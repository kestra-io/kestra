package io.kestra.core.queues;

import io.kestra.core.models.Pauseable;

import java.io.Closeable;

public interface GenericQueueServiceInterface extends Pauseable, Closeable {

    GenericQueueMessage receive(String namespace, String tenant, String topic, Class<? extends GenericQueueMessage> clazz);

    void publish(String namespace, String tenant, String topic, GenericQueueMessage message);
}
