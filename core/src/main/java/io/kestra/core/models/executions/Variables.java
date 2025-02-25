package io.kestra.core.models.executions;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.MapUtils;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.util.*;

@JsonSerialize(using = Variables.Serializer.class)
@JsonDeserialize(using = Variables.Deserializer.class)
public class Variables {
    public static final String TYPE = "io.kestra.datatype:outputs";

    private static final ObjectMapper ION_MAPPER = JacksonMapper.ofIon();
    private static ApplicationContext applicationContext;

    private URI storageUri;

    private transient Map<String, Object> delegate;
    private transient State state;

    private Map<String, Object> loadFromStorage() {
        if (this.state != State.DEFLATED) {
            StorageInterface storage = applicationContext.getBean(StorageInterface.class);
            if (storage == null) {
                throw new IllegalStateException("Internal storage is not initialized");
            }
            if (storageUri == null) {
                throw new IllegalStateException("Storage URI is not set");
            }

            try (InputStream file = storage.get(null, null, storageUri)) { // FIXME
                delegate = ION_MAPPER.readValue(file, JacksonMapper.MAP_TYPE_REFERENCE);
                System.out.println("Reading " + delegate);
                state = State.DEFLATED;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        return MapUtils.emptyOnNull(delegate);
    }

    public static Variables of(Map<String, Object> map) {
        StorageInterface storage = applicationContext.getBean(StorageInterface.class);

        if (map == null || map.isEmpty()) {
            return new Variables(map, null);
        }

        try {
            System.out.println("Storing " + map);
            File file = Files.createTempFile("output-", ".json").toFile();
            ION_MAPPER.writeValue(file, map);
            try (InputStream is = new FileInputStream(file)) {
                URI storageUri = URI.create(file.getName()); // FIXME
                storage.put(null, null, storageUri, is); // FIXME
                return new Variables(map, storageUri);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    public static Variables of(URI uri) {
        return new Variables(uri);
    }

    private Variables(Map<String, Object> map, URI uri) {
        this.storageUri = uri;

        this.delegate = map;
        this.state = State.DEFLATED;
    }

    private Variables(Map<String, Object> map) {
        this.delegate = map;
        this.state = State.LEGACY;
    }

    private Variables(URI uri) {
        this.storageUri = uri;
        this.state = State.INIT;
    }

    public Map<String, Object> toMap() {
        return new HashMap<>(loadFromStorage());
    }

    public static class Serializer extends StdSerializer<Variables> {
        protected Serializer() {
            super(Variables.class);
        }

        @Override
        public void serialize(Variables value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            StorageInterface storage = applicationContext.getBean(StorageInterface.class);
            if (storage == null) {
                throw new IllegalStateException("Internal storage is not initialized");
            }

            // TODO: to avoid too much back and forth on the internal storage, we may want to coalesce empty to null
            //  or tweak other part of the code to have null variables when no output (currently it's an empty map)
            if (value == null) {
                gen.writeNull();
            } else {
                if (value.state == State.LEGACY) {
                    serializers.findValueSerializer(Map.class).serialize(value.delegate, gen, serializers);
                } else {
                    gen.writeStartObject();
                    gen.writeStringField("type", TYPE); // marker to be sure at deserialization time we are a Variable not some random Map
                    gen.writeStringField("storageUri", value.storageUri != null ? value.storageUri.toString() : null);
                    gen.writeEndObject();
                }
            }
        }
    }

    public static class Deserializer extends StdDeserializer<Variables> {
        public Deserializer() {
            super(Variables.class);
        }

        @Override
        public Variables deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
            if (parser.hasToken(JsonToken.VALUE_NULL)) {
                return null;
            } else if (parser.hasToken(JsonToken.START_OBJECT)) {
                // deserialize as map
                Map<String, Object> ret = ctx.readValue(parser, Map.class); // FIXME
                if (TYPE.equals(ret.get("type"))) {
                    String uriString = (String) ret.get("storageUri");
                    if (uriString != null) {
                        URI storageUri = URI.create((String) ret.get("storageUri"));
                        return new Variables(storageUri);
                    }
                    return new Variables(Collections.emptyMap(), null);
                }

                // If the type is not TYPE, a real map has been serialized so we build a Variable with it.
                // It should be an output serialized before Variable even exists.
                return new Variables(ret);
            }
            throw new IllegalArgumentException("Unable to deserialize value as it's not an object");
        }
    }

    @Singleton
    public static class ContextHelper {
        @Inject
        private ApplicationContext applicationContext;

        @EventListener
        void onStartup(final StartupEvent event) {
            Variables.applicationContext = this.applicationContext;
        }
    }

    enum State {LEGACY, INIT, DEFLATED}
}

