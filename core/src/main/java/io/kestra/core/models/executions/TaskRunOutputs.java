package io.kestra.core.models.executions;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.kestra.core.storages.Storage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;


@AllArgsConstructor
@Builder
@JsonSerialize(using = TaskRunOutputs.Serializer.class)
@JsonDeserialize(using = TaskRunOutputs.Deserializer.class)
public class TaskRunOutputs implements Map<String, Object> {
    public static final String TYPE = "io.kestra.datatype:outputs";

    @Getter
    private URI storageUri;

    private transient Storage storage;

    private transient Map<String, Object> delegate;

    private Map<String, Object> loadFromStorage() {
//        if (this.delegate == null) {
//            if (this.storage == null) {
//                throw new RuntimeException("storage is null, could not load outputs");
//            }

//            try {
//                delegate = this.storage.getOutputs();

                if (delegate == null) {
                    delegate = Map.of();
                }
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        }

        return delegate;
    }

    public static TaskRunOutputs of(Map<String, Object> map) {
        return new TaskRunOutputs(null, null, map);
    }

    public static TaskRunOutputs of(URI uri, Storage storage) {
        return new TaskRunOutputs(uri, storage, null);
    }

    public static TaskRunOutputs of(URI uri) {
        return new TaskRunOutputs(uri, null, null);
    }

    @Override
    public int size() {
        return loadFromStorage().size();
    }

    @Override
    public boolean isEmpty() {
        return loadFromStorage().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return loadFromStorage().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return loadFromStorage().containsValue(value);
    }

    @Override
    public Object get(Object key) {
        return loadFromStorage().get(key);
    }

    @Override
    public Object put(String key, Object value) {
        throw new UnsupportedOperationException("Can't put, OutputMap are Read-Only");
    }

    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException("Can't remove, OutputMap are Read-Only");
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        throw new UnsupportedOperationException("Can't putAll, OutputMap are Read-Only");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Can't clear, OutputMap are Read-Only");
    }

    @Override
    public Set<String> keySet() {
        return loadFromStorage().keySet();
    }

    @Override
    public Collection<Object> values() {
        return loadFromStorage().values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return loadFromStorage().entrySet();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Map<String, Object> injectStorage(Map<String, Object> data, Storage storage) {
        Map<String, Object> injectMap = new HashMap<>(data);
        for (var entry: data.entrySet()) {
            if (entry.getValue() instanceof Map map) {
                if (TYPE.equalsIgnoreCase((String)map.get("type"))) {
                    injectMap.put(entry.getKey(), TaskRunOutputs.of(URI.create((String) map.get("storageUri"))));
                }  else {
                    injectMap.put(entry.getKey(), injectStorage((Map<String, Object>) map, storage));
                }
            }
        }
        return injectMap;
    }

    public static class Serializer extends StdSerializer<TaskRunOutputs> {
        protected Serializer() {
            super(TaskRunOutputs.class);
        }

        @Override
        public void serialize(TaskRunOutputs value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeStartObject();

            if (value.storageUri != null) {
                gen.writeStringField("type", TYPE);
                gen.writeStringField("storageUri", value.storageUri.toString());
            } else if (value.delegate != null) {
                for (Map.Entry<String, Object> val : value.delegate.entrySet()) {
                    gen.writeObjectField(val.getKey(), val.getValue());
                }
            }

            gen.writeEndObject();
        }
    }


    public static class Deserializer extends StdDeserializer<Map<String, Object>> {
        public Deserializer() {
            this(null);
        }

        public Deserializer(Class<?> vc) {
            super(vc);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Map<String, Object> deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
            return TaskRunOutputs.of((Map<String, Object>) recursive(ctx.readValue(parser, Map.class)));
        }

        private static Map<?, ?> recursive(Map<?, ?> in) {
            return in
                .entrySet()
                .stream()
                .map(t -> {
                    Object value = t.getValue();

                    if (value instanceof Map<?, ?> map) {
                        value = recursive((Map<?, ?>) map);
                    }

                    if (value instanceof Map<?, ?> map && map.containsKey("storageUri")) {
                        return new AbstractMap.SimpleEntry<>(
                            t.getKey(),
                            TaskRunOutputs.of(URI.create(String.valueOf(map.get("storageUri"))))
                        );
                    } else if (value instanceof Collection<?> collection) {
                        return new AbstractMap.SimpleEntry<>(
                            t.getKey(),
                            collection
                                .stream()
                                .map(o -> o instanceof Map<?, ?> map ? recursive(map) : o)
                                .toList()
                        );
                    } else {
                        return new AbstractMap.SimpleEntry<>(
                            t.getKey(),
                            value
                        );
                    }
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }

//
//    public static class Deserializer extends StdDeserializer<TaskRunOutput> {
//        public Deserializer() {
//            this(null);
//        }
//
//        public Deserializer(Class<?> vc) {
//            super(vc);
//        }
//
//        @Override
//        public TaskRunOutput deserialize(JsonParser parser, DeserializationContext ctx)
//            throws IOException {
//
//            JsonNode node = parser.getCodec().readTree(parser);
//
//            if (node.get("storageUri") != null) {
//                return TaskRunOutput.of(URI.create(node.get("storageUri").asText()));
//            } else {
//                return TaskRunOutput.of(Map.of());
//            }
//        }
//    }
}

