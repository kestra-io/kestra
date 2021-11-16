package io.kestra.runner.kafka.serializers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import io.kestra.core.serializers.JacksonMapper;

import java.io.IOException;
import java.util.Map;

public class JsonDeserializer<T> implements Deserializer<T> {
    private static final ObjectMapper mapper = JacksonMapper.ofJson(false);

    private Class<T> cls;
    private boolean strict;


    public JsonDeserializer(Class<T> cls) {
        super();

        this.cls = cls;
        this.strict = true;
    }

    public JsonDeserializer(Class<T> cls, boolean strict) {
        super();

        this.cls = cls;
        this.strict = strict;
    }

    @Override
    public void configure(Map<String, ?> settings, boolean isKey) {
    }

    @Override
    public T deserialize(String topic, byte[] bytes) {
        if (null == bytes) {
            return null;
        }

        try {
            return mapper.readValue(bytes, this.cls);
        } catch (IOException e) {
            if (strict) {
                throw new SerializationException(e);
            } else {
                return null;
            }
        }
    }
}
