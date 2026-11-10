package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.FetchVersion;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.namespace.NamespaceFileService;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.utils.ListUtils;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.utils.PageableUtils;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ListNamespaceFilesTool implements McpPlatformTool {
    private static final int PAGE_SIZE = 100;
    private static final QueryFilter WITHOUT_ROOT_DIRECTORY = new QueryFilter(QueryFilter.Field.PATH, QueryFilter.Op.NOT_EQUALS, "/", null, null);

    private final NamespaceFileService namespaceFileService;

    @Inject
    public ListNamespaceFilesTool(final NamespaceFileService namespaceFileService) {
        this.namespaceFileService = namespaceFileService;
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
        name = "list_namespace_files",
        value = "List the files and directories stored in a Kestra namespace matching the given per-field filters, " + PAGE_SIZE + " per page: "
            + "PARENT_PATH EQUALS '/dir/' lists the content of one directory, PARENT_PATH STARTS_WITH '/dir/' lists it recursively, QUERY matches part of a path; omit all filters to list every entry. "
            + "Read-only; use this to discover files before reading one with read_namespace_file. "
            + "Returns an object { namespace, files, total, page } where `files` is an array of { path, directory, size, revision, updated } (empty when nothing matches); directory paths end with '/' and have no content. "
            + "`total` is the number of all matching entries, so when it exceeds what was read so far request the next `page`."
    )
    public Result listNamespaceFiles(
        @P(name = "namespace", value = "The namespace whose files to list") String namespace,
        @QueryFilterFormat(QueryFilter.Resource.NAMESPACE_FILE_METADATA) List<QueryFilter> filters,
        @P(name = "page", value = "The 1-based page of entries to read; omit for the first page", required = false) Integer page,
        final AgentCallContext.Context context) {
        int pageNumber = NamespaceFileTools.pageNumber(page);
        List<QueryFilter> effective = new ArrayList<>();
        effective.add(WITHOUT_ROOT_DIRECTORY);
        ListUtils.emptyOnNull(filters).stream()
            .filter(filter -> filter.field() != QueryFilter.Field.NAMESPACE)
            .forEach(effective::add);

        ArrayListTotal<NamespaceFileMetadata> entries = namespaceFileService.findMetadata(
            PageableUtils.from(pageNumber, PAGE_SIZE), context.tenant(), namespace, effective, false, FetchVersion.LATEST
        );

        return new Result(
            namespace,
            entries.stream().map(ListNamespaceFilesTool::toEntry).toList(),
            entries.getTotal(),
            pageNumber
        );
    }

    private static FileEntry toEntry(final NamespaceFileMetadata metadata) {
        return new FileEntry(
            metadata.getPath(),
            metadata.isDirectory(),
            metadata.getSize(),
            metadata.getRevision(),
            metadata.getUpdated() == null ? metadata.getCreated() : metadata.getUpdated()
        );
    }

    public record Result(String namespace, List<FileEntry> files, long total, int page) {
    }

    public record FileEntry(String path, boolean directory, long size, int revision, Instant updated) {
    }
}
