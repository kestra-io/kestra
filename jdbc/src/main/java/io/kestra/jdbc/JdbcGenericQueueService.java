package io.kestra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.Pauseable;
import io.kestra.core.queues.GenericQueueMessage;
import io.kestra.jdbc.runner.GenericJdbcQueue;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
@Slf4j
public class JdbcGenericQueueService implements Closeable, Pauseable {

    private GenericJdbcQueue genericQueue;

    private ObjectMapper mapper;

    @Inject
    public JdbcGenericQueueService(GenericJdbcQueue genericQueue) {
        this.genericQueue = genericQueue;
        this.mapper = new ObjectMapper();
    }

    public GenericQueueMessage receive(String namespace, String tenant, String topic, Class<GenericQueueMessage> clazz) {
        final AtomicReference<GenericQueueMessage> message = new AtomicReference<>();
        genericQueue.receive(namespace, tenant, topic, bytes -> {
            try {
                message.set(mapper.readValue(bytes, clazz));
            } catch (IOException e) {
                throw new KestraRuntimeException("Error deserializing queue message to "
                    + clazz.getName(), e);
            }
        });
        return message.get();
    }

    public void publish(String namespace, String tenant, String topic, GenericQueueMessage message) {
        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            throw new KestraRuntimeException("Error serializing queue message to json bytes", e);
        }
        genericQueue.emit(namespace, tenant, topic, bytes);
    }

    @Override
    public void pause() {
        genericQueue.pause();
    }

    @Override
    public void resume() {
        genericQueue.resume();
    }

    @Override
    public void close() throws IOException {
        genericQueue.close();
    }
}
