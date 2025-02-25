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
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.Storage;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.MapUtils;
import io.kestra.core.utils.ReadOnlyDelegatingMap;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwBiConsumer;

/**
 * A <code>Variables</code> represent a set of output variables.
 * Variables can be stored in-memory or inside the internal storage.
 * <p>
 * The easiest way to construct a <code>Variables</code> object is to use the {@link io.kestra.core.services.VariablesService}.
 *
 * @see io.kestra.core.services.VariablesService
 */
@JsonSerialize(using = Variables.Serializer.class)
@JsonDeserialize(using = Variables.Deserializer.class)
public sealed interface Variables extends Map<String, Object> {
    String TYPE = "io.kestra.datatype:outputs";
    Variables EMPTY = new InMemoryVariables(Collections.emptyMap());

    /**
     * Returns an empty Variables.
     */
    static Variables empty() {
        return EMPTY;
    }

    /**
     * Creates an InMemoryVariables with an output map.
     * This is safer to use {@link io.kestra.core.services.VariablesService#of(io.kestra.core.storages.StorageContext, Map)} instead.
     *
     * @see InMemoryVariables
     * @see io.kestra.core.services.VariablesService
     */
    static Variables inMemory(Map<String, Object> outputs) {
        if (MapUtils.isEmpty(outputs)) {
            return empty();
        }
        return new InMemoryVariables(outputs);
    }

    /**
     * Creates an InStorageVariables with a {@link Storage} and an output map.
     * The output map will be immediately stored inside the internal storage.
     *
     * @see InStorageVariables
     * @see io.kestra.core.services.VariablesService
     */
    static Variables inStorage(Storage storage, Map<String, Object> outputs) {
        if (MapUtils.isEmpty(outputs)) {
            return empty();
        }
        return new InStorageVariables(storage, outputs);
    }

    /**
     * Creates an InStorageVariables with an internal storage URI.
     * The output map will be read lazily from the internal storage URI at access time.
     *
     * @see InStorageVariables
     * @see io.kestra.core.services.VariablesService
     */
    static Variables inStorage(StorageContext storageContext, URI uri) {
        return new InStorageVariables(storageContext, uri);
    }

    record StorageContext(String tenantId, String namespace) {}

    final class InMemoryVariables extends ReadOnlyDelegatingMap<String, Object> implements Variables {
        private final Map<String, Object> delegate;

        InMemoryVariables(Map<String, Object> outputs) {
            this.delegate = outputs;
        }

        @Override
        protected Map<String, Object> getDelegate() {
            return MapUtils.emptyOnNull(delegate);
        }
    }

    final class InStorageVariables extends ReadOnlyDelegatingMap<String, Object> implements Variables {
        private static final ObjectMapper ION_MAPPER = JacksonMapper.ofIon();

        private final URI storageUri;
        private final StorageContext storageContext;

        private Map<String, Object> delegate;
        private State state;

        // we need to store the tenantId and namespace for loading the file from the storage

        InStorageVariables(Storage storage, Map<String, Object> outputs) {
            // expand the map in case it already contains variable in it
            this.delegate = expand(outputs);
            this.state = State.DEFLATED;
            this.storageContext = new StorageContext(storage.namespace().tenantId(), storage.namespace().namespace());

            if (!MapUtils.isEmpty(outputs)) {
                try {
                    File file = Files.createTempFile("output-", ".ion").toFile();
                    ION_MAPPER.writeValue(file, outputs);
                    this.storageUri = storage.putFile(file);
                } catch (IOException e) {
                    // FIXME check if we should not declare it
                    throw new UncheckedIOException(e);
                }
            } else {
                this.storageUri = null;
            }
        }

        InStorageVariables(StorageContext storageContext, URI storageUri) {
            this.storageUri = storageUri;
            this.state = State.INIT;
            this.storageContext = storageContext;
        }

        URI getStorageUri() {
            return storageUri;
        }

        StorageContext getStorageContext() {
            return storageContext;
        }

        @Override
        protected Map<String, Object> getDelegate() {
            return loadFromStorage();
        }

        private Map<String, Object> loadFromStorage() {
            if (this.state == State.INIT) {
                if (storageUri == null) {
                    return Collections.emptyMap();
                }

                StorageInterface storage = KestraContext.getContext().getStorageInterface();
                try (InputStream file = storage.get(storageContext.tenantId(), storageContext.namespace(), storageUri)) {
                    delegate = ION_MAPPER.readValue(file, JacksonMapper.MAP_TYPE_REFERENCE);
                    state = State.DEFLATED;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }

                // check all entries to possibly deflate them also
                return MapUtils.emptyOnNull(expand(delegate));
            }

            return MapUtils.emptyOnNull(delegate);
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> expand(Map<String, Object> variables) {
            if (MapUtils.isEmpty(variables)) {
                return variables;
            }

            return variables.entrySet()
                .stream()
                .map(entry -> {
                    if (entry.getValue() instanceof InStorageVariables var) {
                        return Map.entry(entry.getKey(), (Object) expand(var.loadFromStorage()));
                    } else if (entry.getValue() instanceof Map<?, ?> map) {
                        if (TYPE.equals(map.get("type"))) {
                            String uriString = (String) map.get("storageUri");
                            if (uriString != null) {
                                Map<String, String> storageContextMap = (Map<String, String>) map.get("storageContext");
                                StorageContext storageContext = new StorageContext(storageContextMap.get("tenantId"), storageContextMap.get("namespace"));
                                URI storageUri = URI.create(uriString);
                                InStorageVariables inStorage = new InStorageVariables(storageContext, storageUri);
                                return Map.entry(entry.getKey(), (Object) expand(inStorage.loadFromStorage()));
                            }
                            InStorageVariables inStorage = new InStorageVariables((StorageContext) null, null);
                            return Map.entry(entry.getKey(), (Object) inStorage.loadFromStorage());
                        }
                        else {
                            return Map.entry(entry.getKey(), (Object) expand((Map<String, Object>) map));
                        }
                    } else {
                        return entry;
                    }
                })
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));
        }

        enum State { INIT, DEFLATED }
    }

    class Serializer extends StdSerializer<Variables> {
        protected Serializer() {
            super(Variables.class);
        }

        @Override
        public void serialize(Variables value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                switch (value) {
                    case InMemoryVariables inMemory -> {
                        // we must write entry by entry otherwise nulls are not included
                        gen.writeStartObject();
                        inMemory.getDelegate().forEach(throwBiConsumer((k, v) -> gen.writeObjectField(k, v)));
                        gen.writeEndObject();
                    }
                    case InStorageVariables inStorage -> {
                        gen.writeStartObject();
                        gen.writeStringField("type", TYPE); // marker to be sure at deserialization time it's a Variables not some random Map
                        gen.writeStringField("storageUri", inStorage.getStorageUri() != null ? inStorage.getStorageUri().toString() : null);
                        gen.writeObjectField("storageContext", inStorage.getStorageContext());
                        gen.writeEndObject();
                    }
                }
            }
        }
    }

    class Deserializer extends StdDeserializer<Variables> {
        public Deserializer() {
            super(Variables.class);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Variables deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
            if (parser.hasToken(JsonToken.VALUE_NULL)) {
                return null;
            } else if (parser.hasToken(JsonToken.START_OBJECT)) {
                // deserialize as map
                Map<String, Object> ret = ctx.readValue(parser, Map.class);
                if (TYPE.equals(ret.get("type"))) {
                    String uriString = (String) ret.get("storageUri");
                    if (uriString != null) {
                        Map<String, String> storageContextMap = (Map<String, String>) ret.get("storageContext");
                        StorageContext storageContext = new StorageContext(storageContextMap.get("tenantId"), storageContextMap.get("namespace"));
                        URI storageUri = URI.create(uriString);
                        return new InStorageVariables(storageContext, storageUri);
                    }
                    return new InStorageVariables((StorageContext) null, null);
                }

                // If the type is not TYPE, a real map has been serialized so we build a Variables with it.
                return new InMemoryVariables(ret);
            }
            throw new IllegalArgumentException("Unable to deserialize value as it's not an object");
        }
    }
}

