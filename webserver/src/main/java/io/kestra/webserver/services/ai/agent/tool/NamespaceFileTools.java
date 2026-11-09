package io.kestra.webserver.services.ai.agent.tool;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

import io.kestra.core.storages.NamespaceFile;

/**
 * Path handling shared by the namespace file tools: the same normalization and reserved-path rules as the
 * namespace files REST API, and the translation of storage errors into the exceptions tools report.
 */
final class NamespaceFileTools {
    private static final String FLOWS_FOLDER = "/_flows";
    private static final int MAX_FILE_NAME_LENGTH = 255;

    private NamespaceFileTools() {
    }

    static Path path(final String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("A path is required, starting at the namespace root, e.g. '/scripts/etl.py'.");
        }
        Path normalized = NamespaceFile.normalize(Path.of(path));
        String logical = NamespaceFile.toLogicalPath(normalized);
        if (logical.equals(FLOWS_FOLDER) || logical.startsWith(FLOWS_FOLDER + "/")) {
            throw new IllegalArgumentException("The path '%s' is reserved for flows; use the flow tools to read or change flows.".formatted(logical));
        }
        for (Path component : normalized) {
            if (component.toString().length() > MAX_FILE_NAME_LENGTH) {
                throw new IllegalArgumentException("A file or directory name in '%s' exceeds the maximum length of %d characters.".formatted(logical, MAX_FILE_NAME_LENGTH));
            }
        }
        return normalized;
    }

    static int pageNumber(final Integer page) {
        if (page != null && page < 1) {
            throw new IllegalArgumentException("Page must be 1 or greater, got %d.".formatted(page));
        }
        return page == null ? 1 : page;
    }

    static String displayPath(final NamespaceFile file) {
        return displayPath(file, file.isDirectory());
    }

    static String displayPath(final NamespaceFile file, final boolean directory) {
        String path = NamespaceFile.toLogicalPath(file.filePath());
        return directory && !path.endsWith("/") ? path + "/" : path;
    }

    static RuntimeException failure(final Exception e) {
        if (e instanceof FileNotFoundException) {
            return new IllegalArgumentException(e.getMessage(), e);
        }
        if (e instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalStateException(e.getMessage(), e);
    }
}
