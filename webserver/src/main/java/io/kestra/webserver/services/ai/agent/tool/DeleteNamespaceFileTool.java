package io.kestra.webserver.services.ai.agent.tool;

import java.io.IOException;
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
public class DeleteNamespaceFileTool implements McpPlatformTool {
    private final NamespaceFactory namespaceFactory;

    @Inject
    public DeleteNamespaceFileTool(final NamespaceFactory namespaceFactory) {
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
        return McpToolAnnotations.IDEMPOTENT_DESTRUCTIVE;
    }

    @Tool(
        name = "delete_namespace_file",
        value = "Delete a file or a directory from a Kestra namespace; a directory is deleted with everything it contains, and the parent directory is kept. "
            + "Every revision of a deleted file becomes unreadable. Fails when the path does not exist. "
            + "Returns an object { namespace, deleted } where `deleted` is an array of { path, revision } with one entry per file and directory that was deleted."
    )
    public Result deleteNamespaceFile(
        @P(name = "namespace", value = "The namespace of the file or directory") String namespace,
        @P(name = "path", value = "The path of the file or directory from the namespace root, e.g. '/scripts/old.py'") String path,
        final AgentCallContext.Context context) {
        Path filePath = NamespaceFileTools.path(path);
        Namespace storage = namespaceFactory.of(context.tenant(), namespace);

        try {
            if (!storage.exists(filePath)) {
                throw new IllegalArgumentException("File '%s' was not found in namespace '%s'.".formatted(NamespaceFile.toLogicalPath(filePath), namespace));
            }
            List<NamespaceFile> deleted = storage.delete(filePath);

            return new Result(
                namespace,
                deleted.stream().map(file -> new Deleted(NamespaceFileTools.displayPath(file), file.revision())).toList()
            );
        } catch (IOException e) {
            throw NamespaceFileTools.failure(e);
        }
    }

    public record Result(String namespace, List<Deleted> deleted) {
    }

    public record Deleted(String path, int revision) {
    }
}
