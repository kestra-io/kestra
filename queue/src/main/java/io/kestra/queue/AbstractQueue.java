package io.kestra.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.QueueException;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Either;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractQueue<T extends GenericEvent> {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson(false).copy();
    protected final Class<T> cls;
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    public AbstractQueue(Class<T> cls) {
        this.cls = cls;
    }

    protected String queueName() {
        return "test";
    }

    protected String serialize(T message) throws QueueException {
        try {
            return MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new QueueException("Failed to produce '" + message.getClass() + "'", e);
        }
    }

    protected Either<T, DeserializationException> deserialize(byte[] record) {
        try {
            return Either.left(MAPPER.readValue(record, cls));
        } catch (IOException e) {
            return Either.right(new DeserializationException(e, Arrays.toString(record)));
        }
    }

    protected Either<T, DeserializationException> deserialize(String record) {
        try {
            return Either.left(MAPPER.readValue(record, cls));
        } catch (IOException e) {
            return Either.right(new DeserializationException(e, record));
        }
    }

    public void pause() {
        this.isPaused.set(true);
    }

    public void resume() {
        this.isPaused.set(false);
    }

    public void isPaused() {
        this.isPaused.get();
    }

    public void isRunning() {
        this.isRunning.get();
    }

    public void isClosed() {
        this.isClosed.get();
    }

    public void close() {
        this.isClosed.set(true);
        this.isRunning.set(false);
    }
}
