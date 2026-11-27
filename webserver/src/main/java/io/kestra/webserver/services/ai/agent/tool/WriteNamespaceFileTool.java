package io.kestra.webserver.services.ai.agent.tool;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class WriteNamespaceFileTool implements McpPlatformTool {
    private final NamespaceFactory namespaceFactory;

    @Inject
    public WriteNamespaceFileTool(final NamespaceFactory namespaceFactory) {
        this.namespaceFactory = namespaceFactory;
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
        name = "write_namespace_file",
        value = "Write a UTF-8 text file into a Kestra namespace, creating the missing parent directories. "
            + "Fails when a file already exists at the path unless `overwrite` is true, in which case the file gets a new revision and its previous revisions stay readable with read_namespace_file. "
            + "Returns an object { namespace, path, revision, size } where `revision` is the revision that was written and `size` its length in bytes."
    )
    public Result writeNamespaceFile(
        @P(name = "namespace", value = "The namespace to write the file into") String namespace,
        @P(name = "path", value = "The path of the file from the namespace root, e.g. '/scripts/etl.py'") String path,
        @P(name = "content", value = "The full text content of the file; pass an empty string for an empty file") String content,
        @P(name = "overwrite", value = "Whether to replace an existing file at the path; default false", required = false) Boolean overwrite,
        final AgentCallContext.Context context) {
        Path filePath = NamespaceFileTools.path(path);
        if (content == null) {
            throw new IllegalArgumentException("The content to write is required; pass an empty string for an empty file.");
        }
        boolean replace = Boolean.TRUE.equals(overwrite);
        Namespace storage = namespaceFactory.of(context.tenant(), namespace);

        try {
            if (!replace && storage.exists(filePath)) {
                throw new IllegalStateException(
                    "File '%s' already exists in namespace '%s'; pass overwrite=true to replace it.".formatted(NamespaceFile.toLogicalPath(filePath), namespace)
                );
            }
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            List<NamespaceFile> written = storage.putFile(filePath, new ByteArrayInputStream(bytes), replace ? Namespace.Conflicts.OVERWRITE : Namespace.Conflicts.ERROR);
            NamespaceFile file = written.stream()
                .filter(candidate -> candidate.filePath().equals(filePath))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("File '%s' was not written to namespace '%s'.".formatted(NamespaceFile.toLogicalPath(filePath), namespace)));

            return new Result(namespace, NamespaceFileTools.displayPath(file, false), file.revision(), bytes.length);
        } catch (IOException | URISyntaxException e) {
            throw NamespaceFileTools.failure(e);
        }
    }

    public record Result(String namespace, String path, int revision, long size) {
    }
}
