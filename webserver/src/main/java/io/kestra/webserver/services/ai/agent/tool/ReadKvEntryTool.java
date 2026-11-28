package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.kv.KVType;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ReadKvEntryTool implements McpPlatformTool {
    static final int DEFAULT_MAX_VALUE_LENGTH = 20_000;

    private final KVStoreService kvStoreService;

    @Inject
    public ReadKvEntryTool(final KVStoreService kvStoreService) {
        this.kvStoreService = kvStoreService;
    }

    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.READ;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.AUTO;
    }

    @Override
    public McpToolAnnotations annotations() {
        return McpToolAnnotations.READ_ONLY;
    }

    @Tool(
        name = "read_kv_entry",
        value = "Read one entry of a namespace's KV store in Kestra. Read-only; fails when the key does not exist in that exact namespace (parent namespaces are not searched) or has expired. "
            + "Returns an object { namespace, key, type, value, truncated, revision, description, updateDate, expirationDate } where `type` is one of STRING, NUMBER, BOOLEAN, DATETIME, DATE, DURATION, JSON "
            + "and `value` is the stored value in its own type, unless its text is longer than `maxValueLength` characters (default " + DEFAULT_MAX_VALUE_LENGTH + "): then `value` is that text cut to the limit and `truncated` is true; call again with a larger `maxValueLength` to read the whole value."
    )
    public Result readKvEntry(
        @P(name = "namespace", value = "The namespace of the KV store") String namespace,
        @P(name = "key", value = "The key of the entry") String key,
        @P(name = "maxValueLength", value = "The maximum number of characters of the value to return, a longer value is cut and ends with an ellipsis; omit for the default of " + DEFAULT_MAX_VALUE_LENGTH, required = false) Integer maxValueLength,
        final AgentCallContext.Context context) {
        if (maxValueLength != null && maxValueLength < 1) {
            throw new IllegalArgumentException("maxValueLength must be 1 or greater, got %d.".formatted(maxValueLength));
        }
        int limit = maxValueLength == null ? DEFAULT_MAX_VALUE_LENGTH : maxValueLength;
        KVStore store = kvStoreService.get(context.tenant(), namespace);

        Object value = KvEntryTools.valueOrThrow(store, namespace, key);
        KVEntry entry = KvEntryTools.entryOrThrow(store, namespace, key);

        String text = value instanceof String string ? string : toJson(value);
        boolean truncated = text.length() > limit;

        return new Result(
            namespace,
            key,
            KVType.from(value),
            truncated ? text.substring(0, limit) + "…" : JacksonMapper.ofJson().valueToTree(value),
            truncated,
            entry.revision(),
            entry.description(),
            entry.updateDate(),
            entry.expirationDate()
        );
    }

    private static String toJson(final Object value) {
        try {
            return JacksonMapper.ofJson().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The KV value could not be serialized: %s".formatted(e.getMessage()), e);
        }
    }

    public record Result(String namespace, String key, KVType type, Object value, boolean truncated, Integer revision, String description, Instant updateDate, Instant expirationDate) {
    }
}
