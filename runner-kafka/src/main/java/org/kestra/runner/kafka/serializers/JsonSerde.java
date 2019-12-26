package org.kestra.runner.kafka.serializers;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class JsonSerde<T> implements Serde<T> {
    private final Serializer<T> serializer;
    private final Deserializer<T> deserializer;

    private JsonSerde(Class<T> cls) {
        this.deserializer = new JsonDeserializer<>(cls);
        this.serializer = new JsonSerializer<>();
    }

    public static <T> JsonSerde<T> of(Class<T> cls) {
        return new JsonSerde<>(cls);
    }

    @Override
    public void configure(Map<String, ?> settings, boolean isKey) {
        this.serializer.configure(settings, isKey);
        this.deserializer.configure(settings, isKey);
    }

    @Override
    public void close() {
        this.deserializer.close();
        this.serializer.close();
    }

    @Override
    public Serializer<T> serializer() {
        return this.serializer;
    }

    @Override
    public Deserializer<T> deserializer() {
        return this.deserializer;
    }
}
