package io.kestra.core.models.property;

import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.StorageContext;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Helper class for fetching content from a URI.
 * It supports reading from the following schemes: {@link #SUPPORTED_SCHEMES}.
 */
public class URIFetcher {
    private static final String FILE_SCHEME = "file";
    private static final List<String> SUPPORTED_SCHEMES = List.of(StorageContext.KESTRA_SCHEME, FILE_SCHEME);

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
            case FILE_SCHEME -> {
                Path path = Path.of(uri).toRealPath(); // toRealPath() will protect about path traversal issues
                Path workingDirectory = runContext.workingDir().path();
                if (!path.startsWith(workingDirectory)) {
                    // we need to check that it's on an allowed path
                    List<String> globalAllowedPaths = ((DefaultRunContext) runContext).getApplicationContext().getProperty("kestra.plugins.allowed-paths", List.class, Collections.emptyList());
                    if (globalAllowedPaths.stream().noneMatch(path::startsWith)) {
                        // if not globally allowed, we check it's allowed for this specific plugin
                        List<String> pluginAllowedPaths = (List<String>) runContext.pluginConfiguration("allowed-paths").orElse(Collections.emptyList());
                        if (pluginAllowedPaths.stream().noneMatch(path::startsWith)) {
                            throw new SecurityException("The path " + path + " is not authorized. " +
                                "Only files inside the working directory are allowed by default, other path must be allowed either globally inside the Kestra configuration using the `kestra.plugins.allowed-paths` property, " +
                                "or by plugin using the `allowed-paths` plugin configuration.");
                        }
                    }
                }
                yield new FileInputStream(path.toFile());
            }
            default -> throw new IllegalArgumentException("Scheme not supported: " + uri.getScheme());
        };
    }
}
