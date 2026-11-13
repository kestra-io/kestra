package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.kv.KVType;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CreateKvEntryTool implements McpPlatformTool {
    private final KVStoreService kvStoreService;

    @Inject
    public CreateKvEntryTool(final KVStoreService kvStoreService) {
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
        return McpToolAnnotations.ADDITIVE;
    }

    @Tool(
        name = "create_kv_entry",
        value = "Create an entry in a namespace's KV store in Kestra, which flows then read with the kv() function. Fails when the key already exists in that namespace; use update_kv_entry to change an existing entry. "
            + "The value is given as text and stored typed the way the KV API stores it: a number, boolean, quoted string, ISO date or JSON object/array is kept as such, and any other text is stored as a string. "
            + "Returns an object { namespace, key, type, revision } where `type` is the stored type (STRING, NUMBER, BOOLEAN, DATETIME, DATE, DURATION, JSON) and `revision` is 1."
    )
    public Result createKvEntry(
        @P(name = "namespace", value = "The namespace of the KV store") String namespace,
        @P(name = "key", value = "The key of the entry: alphanumeric characters, dots, underscores and hyphens, starting with an alphanumeric character") String key,
        @P(name = "value", value = "The value as text, e.g. 42, true, \"text\", {\"a\": 1} or plain text") String value,
        @P(name = "description", value = "An optional description of the entry", required = false) String description,
        @P(name = "ttl", value = "An optional time-to-live as an ISO-8601 duration (e.g. PT1H, P7D) after which the entry expires; omit for an entry that never expires", required = false) String ttl,
        final AgentCallContext.Context context) {
        KVStore store = kvStore(context.tenant(), namespace);
        if (KvEntryTools.io(() -> store.exists(key))) {
            throw new IllegalStateException("KV entry '%s' already exists in namespace '%s'; use update_kv_entry to change it.".formatted(key, namespace));
        }

        KVMetadata metadata = new KVMetadata(description, KvEntryTools.ttl(ttl));
        KvEntryTools.io(() ->
        {
            store.put(key, KVValueAndMetadata.ofText(metadata, value), false);
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
