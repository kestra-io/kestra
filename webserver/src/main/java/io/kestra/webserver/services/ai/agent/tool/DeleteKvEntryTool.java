package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class DeleteKvEntryTool implements McpPlatformTool {
    private final KVStoreService kvStoreService;

    @Inject
    public DeleteKvEntryTool(final KVStoreService kvStoreService) {
        this.kvStoreService = kvStoreService;
    }

    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.MUTATE;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.CONFIRM;
    }

    @Override
    public McpToolAnnotations annotations() {
        return McpToolAnnotations.IDEMPOTENT_DESTRUCTIVE;
    }

    @Tool(
        name = "delete_kv_entry",
        value = "Delete an entry from a namespace's KV store in Kestra: flows reading the key with the kv() function no longer find it. Fails when the key does not exist in that namespace. "
            + "Returns an object { namespace, key, revision } where `revision` is the last revision of the deleted entry."
    )
    public Result deleteKvEntry(
        @P(name = "namespace", value = "The namespace of the KV store") String namespace,
        @P(name = "key", value = "The key of the entry") String key,
        final AgentCallContext.Context context) {
        KVStore store = kvStore(context.tenant(), namespace);
        KVEntry entry = KvEntryTools.entryOrThrow(store, namespace, key);

        KvEntryTools.io(() -> store.delete(key));

        return new Result(namespace, key, entry.revision());
    }

    protected KVStore kvStore(final String tenant, final String namespace) {
        return kvStoreService.get(tenant, namespace);
    }

    public record Result(String namespace, String key, Integer revision) {
    }
}
