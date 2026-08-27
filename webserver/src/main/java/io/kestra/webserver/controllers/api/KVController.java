package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Resource;
import io.kestra.core.models.kv.KVType;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.models.namespaces.NamespaceInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.kv.*;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TypeConverter;
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
import jakarta.validation.constraints.Max;

@Controller("/api/v1/{tenant}")
public class KVController {

    @Inject
    private KVStoreService kvStoreService;

    @Inject
    protected TenantService tenantService;

    /**
     * Maps a sortable name to the {@link PersistedKvMetadata} property the repositories sort on:
     * JDBC turns the value into a column by camel-to-snake conversion, Elasticsearch uses it
     * verbatim as the document field. Four {@link KVEntry} field names differ from their property,
     * so an unmapped sort resolved to nothing and failed the query with a 500 — {@code updateDate}
     * being the one the UI exposes.
     *
     * <p>{@code key} is the exception: {@code kv_metadata."key"} is a real column, the primary key
     * holding the uid, so that mapping prevents an ordering on the wrong data rather than a failure.
     *
     * <p>Both spellings are accepted. {@link KVEntry} field names are the documented contract, but
     * the KV table has always sorted on the properties directly — its default sort is
     * {@code name:asc} — so rejecting those would break every existing client. Anything outside
     * both sets yields {@code null}, which {@link PageableUtils} answers with a 422 rather than
     * letting an unknown column reach the query; that still rules out internal columns such as
     * {@code last} and {@code deleted}.
     */
    private static final Map<String, String> SORT_FIELDS = Map.ofEntries(
        // KVEntry field -> PersistedKvMetadata property
        Map.entry("namespace", "namespace"),
        Map.entry("key", "name"),
        Map.entry("revision", "version"),
        Map.entry("description", "description"),
        Map.entry("creationDate", "created"),
        Map.entry("updateDate", "updated"),
        Map.entry("expirationDate", "expirationDate"),
        // the four properties whose name differs, accepted under their own name too
        Map.entry("name", "name"),
        Map.entry("version", "version"),
        Map.entry("created", "created"),
        Map.entry("updated", "updated")
    );

    private String sortMapper(String key) {
	return key == null ? null :SORT_FIELDS.get(key);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get("/kv")
    @Operation(tags = { "KV" }, summary = "List all keys")
    public PagedResults<KVEntry> listAllKeys(
        @Parameter(description = "The current page") @QueryValue(value = "page", defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(value = "size", defaultValue = "10") @Max(PageableUtils.MAX_PAGE_SIZE) int size,
        @Parameter(
            description = "The sort of current page", examples = {
                @ExampleObject(name = "Sort by key in ascending order", value = "key:asc"),
                @ExampleObject(name = "Sort by description in descending order", value = "description:desc"),
            }
        ) @Nullable @QueryValue(value = "sort") List<String> sort,
        @Parameter(description = "Filters. PHP-style nested query is used - example: `filters[namespace][IN]=company.team`") @QueryFilterFormat(Resource.KV_METADATA) List<QueryFilter> filters)
        throws IOException {
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

        KVEntry kvEntry = nsKvStore.get(key).orElseThrow();

        return new KvDetail(KVType.from(value), value, kvEntry.revision(), kvEntry.updateDate());
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
        KVMetadata metadata = new KVMetadata(description, TypeConverter.toDuration(ttl));
        try {
            try (JsonParser parser = JacksonMapper.ofIon().createParser(value)) {
                JsonNode jsonNode = JacksonMapper.ofIon().readTree(parser);
                if (parser.nextToken() != null) {
                    throw new JsonParseException(parser, "Trailing content after the first Ion value");
                }
                kvStore(namespace).put(key, new KVValueAndMetadata(metadata, jsonNode));
            }
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
