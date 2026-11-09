package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.util.List;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.services.KVService;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.utils.PageableUtils;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ListKvEntriesTool implements McpPlatformTool {
    static final int PAGE_SIZE = 50;
    private static final List<String> SORT = List.of("namespace:asc", "name:asc");

    private final KVService kvService;

    @Inject
    public ListKvEntriesTool(final KVService kvService) {
        this.kvService = kvService;
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
        name = "list_kv_entries",
        value = "List the entries of Kestra's namespace KV stores matching the given per-field filters (a key QUERY, NAMESPACE, UPDATED, EXPIRATION_DATE), " + PAGE_SIZE + " per page sorted by namespace then key. "
            + "NAMESPACE with EQUALS matches one namespace, with PREFIX a namespace and its children. Read-only; use this to discover keys before reading one with read_kv_entry. Expired entries are not listed. "
            + "Returns an object { entries, total, page } where `entries` is an array of { namespace, key, revision, description, updateDate, expirationDate } (empty when nothing matches) and never includes values; "
            + "`total` is the number of all matching entries, so when it exceeds what was read so far, request the next `page`."
    )
    public Result listKvEntries(
        @QueryFilterFormat(QueryFilter.Resource.KV_METADATA) List<QueryFilter> filters,
        @P(name = "page", value = "The 1-based page of entries to read; omit for the first page", required = false) Integer page,
        final AgentCallContext.Context context) {
        if (page != null && page < 1) {
            throw new IllegalArgumentException("Page must be 1 or greater, got %d.".formatted(page));
        }
        int pageNumber = page == null ? 1 : page;

        ArrayListTotal<KVEntry> entries = KvEntryTools.io(
            () -> kvService.list(PageableUtils.from(pageNumber, PAGE_SIZE, SORT), context.tenant(), null, filters)
        );

        return new Result(
            entries.stream().map(ListKvEntriesTool::toSummary).toList(),
            entries.getTotal(),
            pageNumber
        );
    }

    private static EntrySummary toSummary(final KVEntry entry) {
        return new EntrySummary(entry.namespace(), entry.key(), entry.revision(), entry.description(), entry.updateDate(), entry.expirationDate());
    }

    public record Result(List<EntrySummary> entries, long total, int page) {
    }

    public record EntrySummary(String namespace, String key, Integer revision, String description, Instant updateDate, Instant expirationDate) {
    }
}
