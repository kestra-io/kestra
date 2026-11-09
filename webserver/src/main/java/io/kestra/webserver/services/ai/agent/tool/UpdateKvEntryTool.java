package io.kestra.webserver.services.ai.agent.tool;

import java.time.Duration;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.kv.KVType;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class UpdateKvEntryTool implements McpPlatformTool {
    private final KVStoreService kvStoreService;

    @Inject
    public UpdateKvEntryTool(final KVStoreService kvStoreService) {
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
        name = "update_kv_entry",
        value = "Replace the value of an existing entry in a namespace's KV store in Kestra; flows reading the key with the kv() function see the new value right away. Fails when the key does not exist in that namespace; use create_kv_entry for a new entry. "
            + "The previous value is kept as an earlier revision. The value is given as text and stored typed the way the KV API stores it: a number, boolean, quoted string, ISO date or JSON object/array is kept as such, and any other text is stored as a string. "
            + "An omitted `description` or `ttl` keeps the entry's current description or expiration date. "
            + "Returns an object { namespace, key, type, revision } where `type` is the stored type (STRING, NUMBER, BOOLEAN, DATETIME, DATE, DURATION, JSON) and `revision` is the new revision."
    )
    public Result updateKvEntry(
        @P(name = "namespace", value = "The namespace of the KV store") String namespace,
        @P(name = "key", value = "The key of the entry") String key,
        @P(name = "value", value = "The new value as text, e.g. 42, true, \"text\", {\"a\": 1} or plain text") String value,
        @P(name = "description", value = "A new description of the entry; omit to keep the current one", required = false) String description,
        @P(name = "ttl", value = "A new time-to-live as an ISO-8601 duration (e.g. PT1H, P7D) counted from now; omit to keep the current expiration date", required = false) String ttl,
        final AgentCallContext.Context context) {
        KVStore store = kvStore(context.tenant(), namespace);
        KVEntry existing = KvEntryTools.entryOrThrow(store, namespace, key);

        String newDescription = description == null ? existing.description() : description;
        Duration newTtl = KvEntryTools.ttl(ttl);
        KVMetadata metadata = newTtl == null
            ? new KVMetadata(newDescription, existing.expirationDate())
            : new KVMetadata(newDescription, newTtl);
        KvEntryTools.io(() ->
        {
            store.put(key, KVValueAndMetadata.ofText(metadata, value));
            return null;
        });

        KVType type = KvEntryTools.typeOf(store, namespace, key);
        return new Result(namespace, key, type, KvEntryTools.entryOrThrow(store, namespace, key).revision());
    }

    protected KVStore kvStore(final String tenant, final String namespace) {
        return kvStoreService.get(tenant, namespace);
    }

    public record Result(String namespace, String key, KVType type, Integer revision) {
    }
}
