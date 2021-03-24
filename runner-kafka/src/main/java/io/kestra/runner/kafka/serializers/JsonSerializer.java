package io.kestra.runner.kafka.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import io.kestra.core.serializers.JacksonMapper;

import java.util.Map;

public class JsonSerializer<T> extends JacksonMapper implements Serializer<T> {
    private static final ObjectMapper mapper = JacksonMapper.ofJson();

    public JsonSerializer() {
        super();
    }

    @Override
    public void configure(Map<String, ?> settings, boolean isKey) {
    }

    @Override
    public byte[] serialize(String topic, T message) {
        if (null == message) {
            return null;
        }
        try {
            return mapper.writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            throw new SerializationException(e);
        }
    }

    public byte[] serialize(T message) {
        return this.serialize("", message);
    }
}
