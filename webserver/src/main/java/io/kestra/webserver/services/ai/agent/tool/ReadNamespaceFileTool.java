package io.kestra.webserver.services.ai.agent.tool;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

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
public class ReadNamespaceFileTool implements McpPlatformTool {
    static final int DEFAULT_MAX_LENGTH = 20_000;

    private final NamespaceFactory namespaceFactory;

    @Inject
    public ReadNamespaceFileTool(final NamespaceFactory namespaceFactory) {
        this.namespaceFactory = namespaceFactory;
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
        name = "read_namespace_file",
        value = "Read a file stored in a Kestra namespace as UTF-8 text, optionally at a specific revision, in pages of at most `maxLength` characters (default " + DEFAULT_MAX_LENGTH + "). "
            + "Read-only; use list_namespace_files to find the path first. Directories have no content. "
            + "Returns an object { namespace, path, revision, offset, content, nextOffset } where `revision` is the revision that was read and `nextOffset` is the offset to pass to read the rest of the file, or null when `content` ends the file."
    )
    public Result readNamespaceFile(
        @P(name = "namespace", value = "The namespace of the file") String namespace,
        @P(name = "path", value = "The path of the file from the namespace root, e.g. '/scripts/etl.py'") String path,
        @P(name = "revision", value = "Optional file revision to read; omit to read the latest revision", required = false) Integer revision,
        @P(name = "offset", value = "The 0-based character offset to start reading at; omit to read from the start", required = false) Integer offset,
        @P(name = "maxLength", value = "The maximum number of characters to return; omit for the default of " + DEFAULT_MAX_LENGTH, required = false) Integer maxLength,
        final AgentCallContext.Context context) {
        Path filePath = NamespaceFileTools.path(path);
        int start = offset == null ? 0 : offset;
        if (start < 0) {
            throw new IllegalArgumentException("Offset must be 0 or greater, got %d.".formatted(start));
        }
        int length = maxLength == null ? DEFAULT_MAX_LENGTH : maxLength;
        if (length < 1) {
            throw new IllegalArgumentException("maxLength must be 1 or greater, got %d.".formatted(length));
        }
        Namespace storage = namespaceFactory.of(context.tenant(), namespace);

        try {
            int read = revision == null ? storage.get(filePath).revision() : revision;
            try (Reader reader = new InputStreamReader(storage.getFileContent(filePath, read), StandardCharsets.UTF_8)) {
                skip(reader, start);
                String content = read(reader, length);
                boolean more = reader.read() != -1;
                return new Result(
                    namespace, NamespaceFileTools.displayPath(NamespaceFile.of(namespace, filePath)), read, start, content, more ? start + content.length() : null
                );
            }
        } catch (IOException e) {
            throw NamespaceFileTools.failure(e);
        }
    }

    private static void skip(final Reader reader, final int characters) throws IOException {
        long remaining = characters;
        while (remaining > 0) {
            long skipped = reader.skip(remaining);
            if (skipped <= 0) {
                return;
            }
            remaining -= skipped;
        }
    }

    private static String read(final Reader reader, final int maxLength) throws IOException {
        char[] buffer = new char[maxLength];
        int total = 0;
        while (total < maxLength) {
            int count = reader.read(buffer, total, maxLength - total);
            if (count < 0) {
                break;
            }
            total += count;
        }
        return new String(buffer, 0, total);
    }

    public record Result(String namespace, String path, int revision, int offset, String content, Integer nextOffset) {
    }
}
