package io.kestra.core.storages;

import jakarta.annotation.Nullable;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a NamespaceFile object.
 *
 * @param path      The path of file relative to the namespace.
 * @param uri       The URI of the namespace file in the Kestra's internal storage.
 * @param namespace The namespace of the file.
 */
public record NamespaceFile(
    Path path,
    URI uri,
    String namespace
) {

    /**
     * Static factory method for constructing a new {@link NamespaceFile} object.
     * <p>
     * This method is equivalent to calling {@code NamespaceFile#of(String, null)}
     *
     * @param namespace The namespace - cannot be {@code null}.
     * @return a new {@link NamespaceFile} object
     */
    public static NamespaceFile of(final String namespace) {
        return of(namespace, (Path) null);
    }

    /**
     * Static factory method for constructing a new {@link NamespaceFile} object.
     *
     * @param uri       The path of file relative to the namespace - cannot be {@code null}.
     * @param namespace The namespace - cannot be {@code null}.
     * @return a new {@link NamespaceFile} object
     */
    public static NamespaceFile of(final String namespace, @Nullable final URI uri) {
        if (uri == null) {
            return of(namespace, (Path) null);
        }

        Path path = Path.of(uri.getPath());
        if (uri.getScheme() != null) {
            if (!uri.getScheme().equalsIgnoreCase("kestra")) {
                throw new IllegalArgumentException(String.format(
                    "Invalid Kestra URI scheme. Expected 'kestra', but was '%s'.", uri.getScheme()
                ));
            }
            if (!uri.getPath().startsWith(StorageContext.namespaceFilePrefix(namespace))) {
                throw new IllegalArgumentException(String.format(
                    "Invalid Kestra URI. Expected prefix for namespace '%s', but was %s.", namespace, uri)
                );
            }
            return of(namespace, Path.of(StorageContext.namespaceFilePrefix(namespace)).relativize(path));
        }
        return of(namespace, path);
    }

    /**
     * Static factory method for constructing a new {@link NamespaceFile} object.
     *
     * @param path      The path of file relative to the namespace.
     * @param namespace The namespace - cannot be {@code null}.
     * @return a new {@link NamespaceFile} object
     */
    public static NamespaceFile of(final String namespace, @Nullable final Path path) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        if (path == null || path.equals(Path.of("/"))) {
            return new NamespaceFile(
                Path.of(""),
                URI.create(StorageContext.KESTRA_PROTOCOL + StorageContext.namespaceFilePrefix(namespace) + "/"),
                namespace
            );
        }

        Path namespacePrefixPath = Path.of(StorageContext.namespaceFilePrefix(namespace));
        Path filePath = path.normalize();
        if (filePath.isAbsolute()) {
            filePath = filePath.getRoot().relativize(filePath);
        }
        return new NamespaceFile(
            filePath,
            URI.create(StorageContext.KESTRA_PROTOCOL + namespacePrefixPath.resolve(filePath)),
            namespace
        );
    }

    /**
     * Returns The path of file relative to the namespace.
     *
     * @param withLeadingSlash specify whether to remove leading slash from the returned path.
     * @return The path.
     */
    public Path path(boolean withLeadingSlash) {
        final String strPath = path.toString();
        if (!withLeadingSlash) {
            if (strPath.startsWith("/")) {
                return Path.of(strPath.substring(1));
            }
        } else {
            if (!strPath.startsWith("/")) {
                return Path.of("/").resolve(path);
            }
        }
        return path;
    }

    /**
     * Get the full storage path of this namespace file.
     *
     * @return The {@link Path}.
     */
    public Path storagePath() {
        return Path.of(uri().getPath());
    }
}
