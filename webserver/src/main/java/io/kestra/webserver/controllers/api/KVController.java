package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.kv.KVType;
import io.kestra.core.models.namespaces.NamespaceInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.kv.*;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.inject.Inject;

@Controller("/api/v1/{tenant}")
public class KVController {

    @Inject
    private KVStoreService kvStoreService;

    @Inject
    protected TenantService tenantService;

    private String sortMapper(String key) {
        if (key != null && key.equals("key")) {
            return "name";
        }
        return key;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get("/kv")
    @Operation(tags = { "KV" }, summary = "List all keys")
    public PagedResults<KVEntry> listAllKeys(
        @Parameter(description = "The current page") @QueryValue(value = "page", defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(value = "size", defaultValue = "10") int size,
        @Parameter(
            description = "The sort of current page", examples = {
                @ExampleObject(name = "Sort by key in ascending order", value = "key:asc"),
                @ExampleObject(name = "Sort by description in descending order", value = "description:desc"),
            }
        ) @Nullable @QueryValue(value = "sort") List<String> sort,
        @Parameter(description = "Filters. PHP-style nested query is used - example: `filters[namespace][IN]=company.team`") @QueryFilterFormat List<QueryFilter> filters) throws IOException {
        return PagedResults.of(kvStoreService.list(PageableUtils.from(page, size, sort, this::sortMapper), tenantService.resolveTenant(), null, filters));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get("/namespaces/{namespace}/kv/inheritance")
    @Operation(tags = { "KV" }, summary = "List all keys for inherited namespaces")
    public List<KVEntry> listKeysWithInheritence(
        @Parameter(description = "The namespace id") @PathVariable String namespace) throws IOException {
        List<String> namespaces = NamespaceInterface.asTree(namespace).stream()
            .filter(ns -> !ns.equals(namespace))
            .toList();
        return getKvEntriesWithInheritance(tenantService.resolveTenant(), namespaces);
    }

    protected List<KVEntry> getKvEntriesWithInheritance(String tenant, List<String> namespaces) throws IOException {
        List<KVEntry> kvEntries = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        List<String> sortedNamespaces = namespaces.stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();
        for (String ns : sortedNamespaces) {
            List<KVEntry> entries = kvStoreService.list(Pageable.UNPAGED, tenant, ns);
            entries.forEach(key ->
            {
                if (!keys.contains(key.key())) {
                    keys.add(key.key());
                    kvEntries.add(key);
                }
            });
        }
        return kvEntries;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/namespaces/{namespace}/kv/{key}")
    @Operation(tags = { "KV" }, summary = "Get value for a key")
    public KvDetail getKeyValue(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The key") @PathVariable String key) throws IOException, ResourceExpiredException {
        KVStore nsKvStore = kvStore(namespace);
        KVValue wrapper = nsKvStore
            .getValue(key)
            .orElseThrow(() -> new NoSuchElementException("No value found for key '" + key + "' in namespace '" + namespace + "'"));
        Object value = wrapper.value();
        if (value instanceof byte[] bytesValue) {
            value = new String(bytesValue);
        }

        // Should never throw as the above verifies the KV entry existence
        KVEntry kvEntry = nsKvStore.get(key).orElseThrow();

        return new KvDetail(KVType.from(value), value, kvEntry.version(), kvEntry.updateDate());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "/namespaces/{namespace}/kv/{key}", consumes = { MediaType.TEXT_PLAIN })
    @Operation(tags = { "KV" }, summary = "Puts a key-value pair in store")
    public void setKeyValue(
        HttpHeaders httpHeaders,
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The key") @PathVariable String key,
        @RequestBody(description = "The value of the key") @Body String value) throws IOException {
        String description = httpHeaders.get("description");
        String ttl = httpHeaders.get("ttl");
        KVMetadata metadata = new KVMetadata(description, ttl == null ? null : Duration.parse(ttl));
        try {
            // use ION mapper to properly handle timestamp
            JsonNode jsonNode = JacksonMapper.ofIon().readTree(value);
            kvStore(namespace).put(key, new KVValueAndMetadata(metadata, jsonNode));
        } catch (JsonProcessingException e) {
            kvStore(namespace).put(key, new KVValueAndMetadata(metadata, value));
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/namespaces/{namespace}/kv/{key}")
    @Operation(tags = { "KV" }, summary = "Delete a key-value pair")
    public boolean deleteKeyValue(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The key") @PathVariable String key) throws IOException {
        return kvStore(namespace).delete(key);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete("/namespaces/{namespace}/kv")
    @Operation(tags = { "KV" }, summary = "Bulk-delete multiple key/value pairs from the given namespace.")
    public HttpResponse<ApiDeleteBulkResponse> deleteKeyValues(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @RequestBody(description = "The keys") @Body ApiDeleteBulkRequest request) {
        KVStore kvStore = kvStore(namespace);
        List<String> deletedKeys = request.keys().stream()
            .map(key ->
            {
                try {
                    if (kvStore.delete(key)) {
                        return Optional.of(key);
                    }
                    return Optional.<String> empty();
                } catch (IOException e) {
                    // Ignore deletion error for bulk-operation
                    return Optional.<String> empty();
                }
            })
            .flatMap(Optional::stream)
            .toList();
        return HttpResponse.ok(new ApiDeleteBulkResponse(deletedKeys));
    }

    protected KVStore kvStore(String namespace) {
        return kvStoreService.get(tenantService.resolveTenant(), namespace);
    }

    /**
     * API Response for the bulk-delete operation.
     *
     * @param keys
     */
    public record ApiDeleteBulkResponse(
        @Parameter(description = "The list of keys deleted") List<String> keys) {

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
        @Parameter(description = "The list of keys to delete") List<String> keys) {

        public List<String> keys() {
            return Optional.ofNullable(keys).orElse(List.of());
        }
    }

    public record KvDetail(
        @Parameter(description = "The type of the KV entry.") KVType type,

        @Parameter(description = "The value of the KV entry.") Object value,

        @Parameter(description = "The revision of the KV entry.") Integer revision,

        @Parameter(description = "The last time the KV entry was updated.") Instant updated) {
    }
}
