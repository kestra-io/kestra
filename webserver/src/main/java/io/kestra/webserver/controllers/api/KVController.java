package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.models.kv.KVType;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.*;
import io.kestra.core.tenant.TenantService;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
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
import java.util.Optional;
import java.util.stream.Collectors;

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
        KVValue wrapper = kvStore(namespace)
            .getValue(key)
            .orElseThrow(() -> new NoSuchElementException("No value found for key '" + key + "' in namespace '" + namespace + "'"));
        Object value = wrapper.value();
        if (value instanceof byte[]) {
            value = new String((byte[]) value);
        }
        return new TypedValue(KVType.from(value), value);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "{key}", consumes = {MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Operation(tags = {"KV"}, summary = "Puts a key-value pair in store")
    public void put(
        HttpHeaders httpHeaders,
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The key") @PathVariable String key,
        @Body String value
    ) throws IOException, URISyntaxException, ResourceExpiredException {
        String ttl = httpHeaders.get("ttl");
        KVMetadata metadata = new KVMetadata(ttl == null ? null : Duration.parse(ttl));
        try {
            // use ION mapper to properly handle timestamp
            JsonNode jsonNode = JacksonMapper.ofIon().readTree(value);
            kvStore(namespace).put(key, new KVValueAndMetadata(metadata, jsonNode));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON value for: " + value);
        }
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

    @ExecuteOn(TaskExecutors.IO)
    @Delete
    @Operation(tags = {"KV"}, summary = "Bulk-delete multiple key/value pairs from the given namespace.")
    public HttpResponse<ApiDeleteBulkResponse> deleteKeys(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The keys") @Body ApiDeleteBulkRequest request
    ) {
        KVStore kvStore = kvStore(namespace);
        List<String> deletedKeys = request.keys().stream()
            .map(key -> {
                try {
                    if (kvStore.delete(key)) {
                        return Optional.of(key);
                    }
                    return Optional.<String>empty();
                } catch (IOException e) {
                    // Ignore deletion error for bulk-operation
                    return Optional.<String>empty();
                }
            })
            .flatMap(Optional::stream)
            .toList();
        return HttpResponse.ok(new ApiDeleteBulkResponse(deletedKeys));
    }

    /**
     * API Response for the bulk-delete operation.
     *
     * @param keys
     */
    @Introspected
    public record ApiDeleteBulkResponse(
        @Parameter(description = "The list of keys deleted")
        List<String> keys
    ) {

        public List<String> keys() {
            return Optional.ofNullable(keys).orElse(List.of());
        }
    }

    /**
     * API Request for the bulk-delete operation.
     *
     * @param keys
     */
    public record ApiDeleteBulkRequest(
        @Parameter(description = "The list of keys to delete")
        List<String> keys
    ) {

        public List<String> keys() {
            return Optional.ofNullable(keys).orElse(List.of());
        }
    }

    private KVStore kvStore(String namespace) {
        return new InternalKVStore(tenantService.resolveTenant(), namespace, storageInterface);
    }

    public record TypedValue(KVType type, Object value) {
    }
}
