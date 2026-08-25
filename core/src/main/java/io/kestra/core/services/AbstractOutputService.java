package io.kestra.core.services;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.InternalStorage;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;

/**
 * Base class for the services managing outputs, either task outputs or execution outputs.
 * It holds the serialization logic and the decision of whether to store an output inside the database or
 * inside the internal storage based on the configured limit.
 *
 * @see TaskOutputService
 * @see ExecutionOutputService
 */
public abstract class AbstractOutputService {
    protected static final ObjectMapper ION_MAPPER = JacksonMapper.ofIon();

    private final StorageInterface storageInterface;
    private final NamespaceFactory namespaceFactory;
    private final int limit;

    protected AbstractOutputService(StorageInterface storageInterface, NamespaceFactory namespaceFactory, int limit) {
        this.storageInterface = storageInterface;
        this.namespaceFactory = namespaceFactory;
        this.limit = limit;
    }

    /**
     * Serialize an output map to its ION representation.
     */
    protected byte[] serialize(Map<String, Object> outputs) throws InternalException {
        try {
            return ION_MAPPER.writeValueAsBytes(outputs);
        } catch (JsonProcessingException e) {
            throw new InternalException(e);
        }
    }

    /**
     * Whether the value should be store in internal storage or not.
     */
    protected boolean shouldStoreInInternalStorage(byte[] value) {
        if (limit < 0) {
            return false;
        }
        return value.length > limit;
    }

    /**
     * Store an output inside the internal storage and returns its URI.
     */
    protected URI storeToInternalStorage(StorageContext context, byte[] outputBytes) throws InternalException {
        try {
            var storage = new InternalStorage(context, storageInterface, namespaceFactory);
            File file = Files.createTempFile("output-", ".ion").toFile();
            Files.write(file.toPath(), outputBytes);
            return storage.putFile(file);
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }

    /**
     * Read an output, either from its inline value or from the internal storage.
     * The storage context is lazily supplied as it is only needed, and only computable, for outputs in internal storage.
     *
     * @return the output map, or <code>null</code> if there is neither a value nor a URI.
     */
    protected Map<String, Object> read(Supplier<StorageContext> context, byte[] value, String uri) throws InternalException {
        try {
            if (value != null) {
                return ION_MAPPER.readValue(value, JacksonMapper.MAP_TYPE_REFERENCE);
            }

            if (uri == null) {
                return null;
            }

            var storage = new InternalStorage(context.get(), storageInterface, namespaceFactory);
            try (var is = storage.getFile(URI.create(uri))) {
                return ION_MAPPER.readValue(is, JacksonMapper.MAP_TYPE_REFERENCE);
            }
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }
}
