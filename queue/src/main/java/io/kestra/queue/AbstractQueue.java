package io.kestra.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.QueueException;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Either;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;

public abstract class AbstractQueue<T extends GenericEvent> {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson(false).copy();
    protected final Class<T> cls;
    protected final ExecutorService executorService;

    public AbstractQueue(Class<T> cls, ExecutorService executorService) {
        this.cls = cls;
        this.executorService = executorService;
    }

    protected String queueName() {
        return "test";
    }

    public void execute(Runnable runnable) {
        this.executorService.execute(runnable);
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
}
