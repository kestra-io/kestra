package io.kestra.webserver.services.ai.agent.tool;

import java.io.IOException;
import java.nio.file.Path;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CreateNamespaceDirectoryTool implements McpPlatformTool {
    private final NamespaceFactory namespaceFactory;

    @Inject
    public CreateNamespaceDirectoryTool(final NamespaceFactory namespaceFactory) {
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
        return McpToolAnnotations.IDEMPOTENT_UPDATE;
    }

    @Tool(
        name = "create_namespace_directory",
        value = "Create an empty directory in a Kestra namespace, along with its missing parent directories; creating a directory that already exists changes nothing. "
            + "write_namespace_file creates parent directories on its own, so this is only needed for a directory that holds no file yet. "
            + "Returns an object { namespace, path } where `path` is the directory path ending with '/'."
    )
    public Result createNamespaceDirectory(
        @P(name = "namespace", value = "The namespace to create the directory in") String namespace,
        @P(name = "path", value = "The path of the directory from the namespace root, e.g. '/scripts/lib'") String path,
        final AgentCallContext.Context context) {
        Path directoryPath = NamespaceFileTools.path(path);

        try {
            NamespaceFile directory = namespaceFactory.of(context.tenant(), namespace).createDirectory(directoryPath);
            return new Result(namespace, NamespaceFileTools.displayPath(directory, true));
        } catch (IOException e) {
            throw NamespaceFileTools.failure(e);
        }
    }

    public record Result(String namespace, String path) {
    }
}
