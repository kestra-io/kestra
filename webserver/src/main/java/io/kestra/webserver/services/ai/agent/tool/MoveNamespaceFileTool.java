package io.kestra.webserver.services.ai.agent.tool;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

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
public class MoveNamespaceFileTool implements McpPlatformTool {
    private final NamespaceFactory namespaceFactory;

    @Inject
    public MoveNamespaceFileTool(final NamespaceFactory namespaceFactory) {
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
        name = "move_namespace_file",
        value = "Move or rename a file or a directory within a Kestra namespace; a directory moves with everything it contains. "
            + "Fails when the source does not exist, when something already exists at the destination, or when the destination is inside the source directory. "
            + "Returns an object { namespace, moved } where `moved` is an array of { from, to, revision } with one entry per file and directory that was moved."
    )
    public Result moveNamespaceFile(
        @P(name = "namespace", value = "The namespace of the file or directory") String namespace,
        @P(name = "from", value = "The current path from the namespace root, e.g. '/scripts/old.py'") String from,
        @P(name = "to", value = "The destination path from the namespace root, e.g. '/scripts/new.py'") String to,
        final AgentCallContext.Context context) {
        Path source = NamespaceFileTools.path(from);
        Path target = NamespaceFileTools.path(to);
        Namespace storage = namespaceFactory.of(context.tenant(), namespace);

        try {
            if (!storage.exists(source)) {
                throw new IllegalArgumentException("File '%s' was not found in namespace '%s'.".formatted(NamespaceFile.toLogicalPath(source), namespace));
            }
            List<Pair<NamespaceFile, NamespaceFile>> moves = storage.move(source, target);

            Map<String, Moved> moved = new LinkedHashMap<>();
            for (Pair<NamespaceFile, NamespaceFile> move : moves) {
                boolean directory = move.getRight().isDirectory();
                Moved entry = new Moved(
                    NamespaceFileTools.displayPath(move.getLeft(), directory), NamespaceFileTools.displayPath(move.getRight(), directory), move.getRight().revision()
                );
                moved.put(entry.to(), entry);
            }
            return new Result(namespace, List.copyOf(moved.values()));
        } catch (Exception e) {
            throw NamespaceFileTools.failure(e);
        }
    }

    public record Result(String namespace, List<Moved> moved) {
    }

    public record Moved(String from, String to, int revision) {
    }
}
