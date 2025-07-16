package io.kestra.core.models.property;

import io.kestra.core.runners.LocalPath;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.StorageContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * Helper class for fetching content from a URI.
 * It supports reading from the following schemes: {@link #SUPPORTED_SCHEMES}.
 */
public class URIFetcher {
    private static final List<String> SUPPORTED_SCHEMES = List.of(StorageContext.KESTRA_SCHEME, LocalPath.FILE_SCHEME, Namespace.NAMESPACE_FILE_SCHEME);

    private final URI uri;

    /**
     * Build a new URI Fetcher from a String URI.
     * WARNING: the URI must be rendered before.
     *
     * A factory method is also provided for fluent style programming, see {@link #of(String).}
     */
    public URIFetcher(String uri) {
        this(URI.create(uri));
    }

    /**
     * Build a new URI Fetcher from a URI.
     *
     * A factory method is also provided for fluent style programming, see {@link #of(URI).}
     */
    public URIFetcher(URI uri) {
        if (SUPPORTED_SCHEMES.stream().noneMatch(s -> s.equals(uri.getScheme()))) {
            throw new IllegalArgumentException("Scheme not supported: " + uri.getScheme() + ". Supported schemes are: " + SUPPORTED_SCHEMES);
        }

        this.uri = uri;
    }

    /**
     * Build a new URI Fetcher from a String URI.
     * WARNING: the URI must be rendered before.
     */
    public static URIFetcher of(String uri) {
        return new URIFetcher(uri);
    }

    /**
     * Build a new URI Fetcher from a URI.
     */
    public static URIFetcher of(URI uri) {
        return new URIFetcher(uri);
    }

    /**
     * Whether the URI is supported by the Fetcher.
     * A supported URI is a string that starts with one of the {@link #SUPPORTED_SCHEMES}.
     */
    public static boolean supports(String uri) {
        return SUPPORTED_SCHEMES.stream().anyMatch(scheme -> uri.startsWith(scheme + "://"));
    }

    /**
     * Whether the URI is supported by the Fetcher.
     * A supported URI is a URI which scheme is one of the {@link #SUPPORTED_SCHEMES}.
     */
    public static boolean supports(URI uri) {
        return uri.getScheme() != null && SUPPORTED_SCHEMES.contains(uri.getScheme());
    }

    /**
     * Fetch the resource pointed by this SmartURI
     *
     * @throws IOException if an IO error occurs
     * @throws SecurityException if the URI points to a path that is not allowed
     */
    public InputStream fetch(RunContext runContext) throws IOException {
        if (uri == null) {
            return InputStream.nullInputStream();
        }

        // we need to first check the protocol, then create one reader by protocol
        return switch (uri.getScheme()) {
            case StorageContext.KESTRA_SCHEME -> runContext.storage().getFile(uri);
            case LocalPath.FILE_SCHEME -> runContext.localPath().get(uri);
            case Namespace.NAMESPACE_FILE_SCHEME -> {
                var namespace = uri.getAuthority() == null ? runContext.storage().namespace() : runContext.storage().namespace(uri.getAuthority());
                var nsFileUri = namespace.get(Path.of(uri.getPath())).uri();
                yield runContext.storage().getFile(nsFileUri);
            }
            default -> throw new IllegalArgumentException("Scheme not supported: " + uri.getScheme());
        };
    }
}
