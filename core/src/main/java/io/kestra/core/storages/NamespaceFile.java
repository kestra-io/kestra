package io.kestra.core.storages;

import io.kestra.core.models.namespaces.files.NamespaceFileMetadata;
import io.kestra.core.utils.WindowsUtils;
import jakarta.annotation.Nullable;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a NamespaceFile object.
 *
 * @param path      The path of file relative to the namespace.
 * @param uri       The URI of the namespace file in the Kestra's internal storage.
 * @param namespace The namespace of the file.
 * @param version The version of the file.
 */
public record NamespaceFile(
    String path,
    URI uri,
    String namespace,
    int version
) {
    private static final Pattern capturePathWithoutVersion = Pattern.compile("(.*)(?:\\.v\\d+)?$");

    public NamespaceFile(Path path, URI uri, String namespace) {
        this(path.toString(), uri, namespace, 1);
    }

    public NamespaceFile(String path, URI uri, String namespace) {
        this(path, uri, namespace, 1);
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
        return of(namespace, (Path) null, 1);
    }

    public static NamespaceFile of(final String namespace, final URI uri) {
        return of(namespace, uri, 1);
    }

    public static NamespaceFile fromMetadata(final NamespaceFileMetadata metadata) {
        return of(
            metadata.getNamespace(),
            metadata.getPath(),
            metadata.getVersion()
        );
    }

    /**
     * Static factory method for constructing a new {@link NamespaceFile} object.
     *
     * @param uri       The path of file relative to the namespace or fully qualified URI.
     * @param namespace The namespace - cannot be {@code null}.
     * @return a new {@link NamespaceFile} object
     */
    public static NamespaceFile of(final String namespace, @Nullable final URI uri, int version) {
        if (uri == null || uri.equals(URI.create("/"))) {
            return of(namespace, (Path) null, version);
        }

        Path path = Path.of(WindowsUtils.windowsToUnixPath(uri.getPath()));
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
            namespaceFile = of(namespace, Path.of(StorageContext.namespaceFilePrefix(namespace)).relativize(path), version);
        } else {
            namespaceFile = of(namespace, path, version);
        }

        boolean trailingSlash = uri.toString().endsWith("/");
        if (!trailingSlash) {
            return namespaceFile;
        }

        // trailing slash on URI is used to identify directory.
        return new NamespaceFile(
            namespaceFile.path,
            URI.create(namespaceFile.uri.toString() + "/"),
            namespaceFile.namespace,
            version
        );
    }

    public static NamespaceFile of(final String namespace, final Path path) {
        return of(namespace, path, 1);
    }

    /**
     * Static factory method for constructing a new {@link NamespaceFile} object.
     *
     * @param path      The path of file relative to the namespace.
     * @param namespace The namespace - cannot be {@code null}.
     * @return a new {@link NamespaceFile} object
     */
    public static NamespaceFile of(final String namespace, @Nullable final Path path, int version) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        if (path == null || path.equals(Path.of("/"))) {
            return new NamespaceFile(
                "",
                URI.create(StorageContext.KESTRA_PROTOCOL + StorageContext.namespaceFilePrefix(namespace) + "/"),
                namespace,
                // Directory always has a single version
                1
            );
        }

        return of(namespace, path.toString(), version);
    }

    public static NamespaceFile of(String namespace, String path, int version) {
        Path namespacePrefixPath = Path.of(StorageContext.namespaceFilePrefix(namespace));
        // Need to remove starting trailing slash for Windows
        String pathWithoutLeadingSlash = path.replaceFirst("^[.]*[\\\\|/]+", "");

        version = NamespaceFile.isDirectory(pathWithoutLeadingSlash) ? 1 : version;

        String storagePath = pathWithoutLeadingSlash;
        if (!pathWithoutLeadingSlash.endsWith("/") && version > 1) {
            storagePath += ".v" + version;
        }

        return new NamespaceFile(
            pathWithoutLeadingSlash,
            URI.create(StorageContext.KESTRA_PROTOCOL + namespacePrefixPath.resolve(storagePath).toString().replace("\\", "/") + (isDirectory(path) ? "/" : "")),
            namespace,
            version
        );
    }

    public static Path normalize(Path path) {
        if(path == null){
            return Path.of("/");
        }
        String compatiblePath = toLogicalPath(path);
        if(!compatiblePath.startsWith("/")){
            compatiblePath = "/" + compatiblePath;
        }
        return Path.of(compatiblePath);
    }

    /**
     * Returns the path of file relative to the namespace.
     * @return The path.
     */
    public Path filePath() {
        String strPath = path;
        Matcher matcher = capturePathWithoutVersion.matcher(strPath);
        if (matcher.matches()) {
            strPath = matcher.group(1);
        }

        return normalize(Path.of(strPath));
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
    public static boolean isDirectory(String path) {
        return path.endsWith("/");
    }

    public boolean isDirectory() {
        return isDirectory(uri.toString());
    }

    /**
     * Checks whether this namespace file is the namespace file root directory.
     *
     * @return {@code true} if this namespace file is the root directory. Otherwise {@code false}.
     */
    public boolean isRootDirectory() {
        return equals(NamespaceFile.of(namespace));
    }

    /**
     * Converts a {@link Path} to a canonical **logical path** string.
     * <p>
     * Logical paths use forward slashes ('/') as separators regardless of the OS.
     * This is useful for namespace storage, URI construction, or any cross-platform
     * path handling where OS-dependent separators should be avoided.
     *
     * @param path the {@link Path} to convert
     * @return a String representing the logical path with forward slashes
     */
    public static String toLogicalPath(Path path){ return toLogicalPath(path.toString());}

    /**
     * Converts a path string to a canonical **logical path**.
     * <p>
     * Replaces all backslashes ('\') with forward slashes ('/') to ensure
     * cross-platform consistency.
     *
     * @param path the path string to convert
     * @return a String representing the logical path with forward slashes
     */
    public static String toLogicalPath(String path) {
        return path.replace("\\", "/");
    }
}
