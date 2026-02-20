package io.kestra.webserver.services.ai;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVEntry;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class NamespaceContextTool {
    private final KVStoreService kvStoreService;

    public NamespaceContextTool(KVStoreService kvStoreService) {
        this.kvStoreService = kvStoreService;
    }

    @Tool("Retrieves KV Store keys available in the namespace. Use this when the user wants to read from or write to the KV Store, or needs to see what keys already exist. Returns a JSON object with namespace and list of key names with their metadata.")
    public String getKvStoreKeys(
        @P("The namespace to query") String namespace,
        @P("The tenant ID (can be null for single-tenant)") String tenantId
    ) {
        try {
            KVStore kvStore = kvStoreService.get(tenantId, namespace, namespace);
            List<KVEntry> entries = kvStore.list();

            List<Map<String, Object>> keyInfo = entries.stream()
                .map(entry -> Map.<String, Object>of(
                    "key", entry.key(),
                    "description", entry.description() != null ? entry.description() : "",
                    "updateDate", entry.updateDate().toString()
                ))
                .collect(Collectors.toList());

            return JacksonMapper.ofJson().writeValueAsString(Map.of(
                "namespace", namespace,
                "kvKeys", keyInfo
            ));
        } catch (IOException e) {
            log.warn("Failed to retrieve KV store keys for namespace {}", namespace, e);
            return "{}";
        }
    }
}
