package io.kestra.webserver.controllers.api;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStoreValueWrapper;
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
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URISyntaxException;
import java.time.Duration;
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
    @Get(uri = "{key}", produces = MediaType.TEXT_PLAIN)
    @Operation(tags = {"KV"}, summary = "Get value for a key")
    public String get(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The key") @PathVariable String key
    ) throws IOException, URISyntaxException, ResourceExpiredException {
        return kvStore(namespace).getRaw(key).orElseThrow(() -> new NoSuchElementException("No value found for key '" + key + "' in namespace '" + namespace + "'"));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{key}", consumes = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(tags = {"KV"}, summary = "Add a key-value pair to store")
    public void put(
        HttpHeaders httpHeaders,
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The key") @PathVariable String key,
        @Body String value
    ) throws IOException, URISyntaxException, ResourceExpiredException {
        String ttl = httpHeaders.get("ttl");
        KVMetadata kvMetadata = new KVMetadata(ttl == null ? null : Duration.parse(ttl));
        kvStore(namespace).putRaw(key, new KVStoreValueWrapper<>(kvMetadata, value));
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

    private InternalKVStore kvStore(String namespace) {
        return new InternalKVStore(tenantService.resolveTenant(), namespace, storageInterface);
    }
}
