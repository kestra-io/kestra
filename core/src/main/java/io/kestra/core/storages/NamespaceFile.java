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
    String path,
    URI uri,
    String namespace
) {

    public NamespaceFile(Path path, URI uri, String namespace) {
        this(path.toString(), uri, namespace);
    }

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
     * @param uri       The path of file relative to the namespace or fully qualified URI.
     * @param namespace The namespace - cannot be {@code null}.
     * @return a new {@link NamespaceFile} object
     */
    public static NamespaceFile of(final String namespace, @Nullable final URI uri) {
        if (uri == null || uri.equals(URI.create("/"))) {
            return of(namespace, (Path) null);
        }

        Path path = Path.of(uri.getPath());

        final NamespaceFile namespaceFile;
        if (uri.getScheme() != null) {
            if (!uri.getScheme().equalsIgnoreCase("kestra")) {
                throw new IllegalArgumentException(String.format(
                    "Invalid Kestra URI scheme. Expected 'kestra', but was '%s'.", uri
                ));
            }
            if (!uri.getPath().startsWith(StorageContext.namespaceFilePrefix(namespace))) {
                throw new IllegalArgumentException(String.format(
                    "Invalid Kestra URI. Expected prefix for namespace '%s', but was %s.", namespace, uri)
                );
            }
            namespaceFile = of(namespace, Path.of(StorageContext.namespaceFilePrefix(namespace)).relativize(path));
        } else {
            namespaceFile = of(namespace, path);
        }

        boolean trailingSlash = uri.toString().endsWith("/");
        if (!trailingSlash) {
            return namespaceFile;
        }

        // trailing slash on URI is used to identify directory.
        return new NamespaceFile(
            namespaceFile.path,
            URI.create(namespaceFile.uri.toString() + "/"),
            namespaceFile.namespace
        );
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
                "",
                URI.create(StorageContext.KESTRA_PROTOCOL + StorageContext.namespaceFilePrefix(namespace) + "/"),
                namespace
            );
        }

        Path namespacePrefixPath = Path.of(StorageContext.namespaceFilePrefix(namespace));
        Path filePath = path.normalize();
        if (filePath.isAbsolute()) {
            filePath = filePath.getRoot().relativize(filePath);
        }
        // Need to remove starting trailing slash for Windows
        String pathWithoutTrailingSlash = path.toString().replaceFirst("^[.]*[\\\\|/]*", "");

        return new NamespaceFile(
            pathWithoutTrailingSlash,
            URI.create(StorageContext.KESTRA_PROTOCOL + namespacePrefixPath.resolve(pathWithoutTrailingSlash).toString().replace("\\","/")),
            namespace
        );
    }

    /**
     * Returns the path of file relative to the namespace.
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
        return Path.of(path);
    }

    /**
     * Get the full storage path of this namespace file.
     *
     * @return The {@link Path}.
     */
    public Path storagePath() {
        return Path.of(uri().getPath());
    }

    /**
     * Checks whether this namespace file is a directory.
     * <p>
     * By default, a namespace file is considered a directory if its URI ends with "/".
     *
     * @return {@code true} if this namespace file is a directory.
     */
    public boolean isDirectory() {
        return uri.toString().endsWith("/");
    }

    /**
     * Checks whether this namespace file is the namespace file root directory.
     *
     * @return {@code true} if this namespace file is the root directory. Otherwise {@code false}.
     */
    public boolean isRootDirectory() {
        return equals(NamespaceFile.of(namespace));
    }
}
