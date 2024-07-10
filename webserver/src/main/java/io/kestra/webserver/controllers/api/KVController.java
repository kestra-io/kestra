package io.kestra.webserver.controllers.api;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.*;
import io.kestra.core.tenant.TenantService;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;

import java.io.*;
import java.net.URISyntaxException;
import java.time.*;
import java.util.List;
import java.util.NoSuchElementException;

@Validated
@Controller("/api/v1/namespaces/{namespace}/kv")
public class KVController {
    @Inject
    private StorageInterface storageInterface;
    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get
    @Operation(tags = {"KV"}, summary = "List all keys for a namespace")
    public List<KVEntry> list(
        @Parameter(description = "The namespace id") @PathVariable String namespace
    ) throws IOException, URISyntaxException {
        return kvStore(namespace).list();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{key}")
    @Operation(tags = {"KV"}, summary = "Get value for a key")
    public TypedValue get(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The key") @PathVariable String key
    ) throws IOException, URISyntaxException, ResourceExpiredException {
        Object value = kvStore(namespace).get(key).orElseThrow(() -> new NoSuchElementException("No value found for key '" + key + "' in namespace '" + namespace + "'"));
        return new TypedValue(KVType.from(value), value);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "{key}", consumes = {MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    @Operation(tags = {"KV"}, summary = "Puts a key-value pair in store")
    public void put(
        HttpHeaders httpHeaders,
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The key") @PathVariable String key,
        @Body String value
    ) throws IOException, URISyntaxException, ResourceExpiredException {
        String ttl = httpHeaders.get("ttl");
        KVMetadata kvMetadata = new KVMetadata(ttl == null ? null : Duration.parse(ttl));
        String ionValue = value;
        if (MediaType.APPLICATION_JSON_TYPE == httpHeaders.contentType().orElse(null)) {
            ionValue = JacksonMapper.ofIon().writeValueAsString(JacksonMapper.toObject(value));
        }
        kvStore(namespace).putRaw(key, new KVStoreValueWrapper<>(kvMetadata, ionValue));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "{key}")
    @Operation(tags = {"KV"}, summary = "Delete a key-value pair")
    public boolean delete(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The key") @PathVariable String key
    ) throws IOException, URISyntaxException, ResourceExpiredException {
        return kvStore(namespace).delete(key);
    }

    private KVStore kvStore(String namespace) {
        return new InternalKVStore(tenantService.resolveTenant(), namespace, storageInterface);
    }


    public enum KVType {
        STRING,
        NUMBER,
        BOOLEAN,
        DATETIME,
        DATE,
        DURATION,
        JSON;

        public static KVType from(Object value) {
            return switch (value) {
                case String ignored -> STRING;
                case Number ignored -> NUMBER;
                case Boolean ignored -> BOOLEAN;
                case LocalDateTime ignored -> DATETIME;
                case Instant ignored -> DATETIME;
                case LocalDate ignored -> DATE;
                case Duration ignored -> DURATION;
                default -> JSON;
            };
        }
    }

    public record TypedValue(KVType type, Object value) {
    }
}
