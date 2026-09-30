package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;

import io.kestra.core.utils.FileUtils;
import io.kestra.webserver.services.UiIndexService;
import io.kestra.webserver.utils.HttpCacheUtils;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.inject.Inject;

/**
 * Serves the UI single-page application off the IO executor instead of the Netty event loop, with entity tags
 * derived from the resource metadata alone so {@code If-None-Match} answers 304 without opening a stream.
 * Unresolved paths fall back to the rewritten {@code index.html}
 * (<a href="https://router.vuejs.org/guide/essentials/history-mode.html">HTML5 history mode</a>).
 */
@Controller("/ui")
@Requires(property = "kestra.webserver.ui.enabled", notEquals = "false", defaultValue = "true")
@Hidden
public class UiController {
    private static final String HASHED_ASSETS_PREFIX = "assets/";
    // Vite output under assets/ is content-hashed, so it can be cached forever.
    private static final String HASHED_ASSETS_CACHE_CONTROL = "public, max-age=31536000, immutable";
    private static final String DEFAULT_CACHE_CONTROL = "public, max-age=86400";
    private static final String INDEX_HTML = "index.html";
    private static final Map<String, Long> JAR_LAST_MODIFIED = new ConcurrentHashMap<>();
    // Extensions the UI build emits that Micronaut has no media type for. Falling through to
    // application/octet-stream would make the browser reject the resource, so they are named here;
    // UiControllerTest fails if the build starts emitting another unmapped extension.
    private static final Map<String, MediaType> EXTRA_MEDIA_TYPES = Map.of(
        "webmanifest", MediaType.of("application/manifest+json")
    );

    private final UiIndexService uiIndexService;

    @Inject
    public UiController(UiIndexService uiIndexService) {
        this.uiIndexService = Objects.requireNonNull(uiIndexService);
    }

    @Get
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<?> index(HttpRequest<?> request) {
        return serve(request, "");
    }

    @Get("/{path:.*}")
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<?> serve(HttpRequest<?> request, @PathVariable String path) {
        String normalized = path == null ? "" : path;
        // A directory-based classpath entry resolves '..' against the real filesystem, so a traversal
        // segment would read outside the UI tree.
        if (!FileUtils.isSafeRelativePath(normalized)) {
            return HttpResponse.notFound();
        }

        // Only paths whose last segment has an extension can be static resources; SPA routes never resolve here.
        if (isStaticResourceCandidate(normalized) && !INDEX_HTML.equals(normalized)) {
            URL url = UiController.class.getClassLoader().getResource("ui/" + normalized);
            if (url != null) {
                return respond(request, normalized, url);
            }
        }

        // SPA history-mode fallback: any unresolved /ui/** path serves the rewritten index.html.
        Optional<? extends HttpResponse<?>> index = uiIndexService.render(request);
        return index.isPresent() ? index.get() : HttpResponse.notFound();
    }

    private HttpResponse<?> respond(HttpRequest<?> request, String normalized, URL url) {
        MediaType mediaType = mediaTypeFor(normalized);
        String cacheControl = normalized.startsWith(HASHED_ASSETS_PREFIX) ? HASHED_ASSETS_CACHE_CONTROL : DEFAULT_CACHE_CONTROL;

        // One connection per request: the metadata and, when the body is needed, the stream both come
        // from it. Opening a second one to read the entity tag doubled the per-request cost.
        URLConnection connection;
        try {
            connection = url.openConnection();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        ResourceMeta meta = metaOf(connection);
        String etag = meta == null ? null : HttpCacheUtils.etag(meta.tag());

        if (etag != null && HttpCacheUtils.anyEtagMatches(request.getHeaders().get(HttpHeaders.IF_NONE_MATCH), etag)) {
            release(connection);
            return applyHeaders(HttpResponse.notModified(), etag, cacheControl);
        }

        InputStream stream = openStream(connection);
        StreamedFile body = meta == null
            ? new StreamedFile(stream, mediaType)
            : new StreamedFile(stream, mediaType, meta.lastModified(), meta.size());
        return applyHeaders(HttpResponse.ok(body), etag, cacheControl);
    }

    private static <T> MutableHttpResponse<T> applyHeaders(MutableHttpResponse<T> response, @Nullable String etag, String cacheControl) {
        response
            .header(HttpHeaders.CACHE_CONTROL, cacheControl)
            // the entity tag names the identity content, so caches must key on the coding the server applied
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
        if (etag != null) {
            response.header(HttpHeaders.ETAG, etag);
        }
        return response;
    }

    static MediaType mediaTypeFor(String path) {
        String extension = NameUtils.extension(path).toLowerCase(Locale.ROOT);
        return MediaType
            .forExtension(extension)
            .orElseGet(() -> EXTRA_MEDIA_TYPES.getOrDefault(extension, MediaType.APPLICATION_OCTET_STREAM_TYPE));
    }

    private static boolean isStaticResourceCandidate(String path) {
        if (path.isEmpty() || path.endsWith("/")) {
            return false;
        }
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        return lastSegment.indexOf('.') > 0;
    }

    private static InputStream openStream(URLConnection connection) {
        try {
            return connection.getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Resolves size, last-modified and a content-identifying tag from the connection's metadata alone,
     * without reading the resource: the zip entry CRC for jar resources, length and mtime for plain
     * files. Returns null when metadata cannot be read; the caller then streams without validators.
     */
    @Nullable
    private static ResourceMeta metaOf(URLConnection connection) {
        try {
            if (connection instanceof JarURLConnection jarConnection) {
                JarEntry entry = jarConnection.getJarEntry();
                if (entry == null) {
                    return null;
                }
                // The entry timestamp is a fixed constant in a reproducible build, so it can neither
                // identify the content nor serve as a meaningful Last-Modified: the tag comes from the
                // CRC, and the enclosing archive's mtime is what actually moves when the build changes.
                return new ResourceMeta(
                    entry.getSize(),
                    jarLastModified(jarConnection),
                    Long.toHexString(entry.getCrc()) + "-" + Long.toHexString(entry.getSize())
                );
            }
            long length = connection.getContentLengthLong();
            long lastModified = connection.getLastModified();
            return new ResourceMeta(length, lastModified, Long.toHexString(length) + "-" + Long.toHexString(lastModified));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * @return the mtime of the archive the entry lives in, memoised per archive because every UI resource
     *     resolves to the same one, or 0 when it cannot be read (Micronaut then omits Last-Modified).
     */
    private static long jarLastModified(JarURLConnection connection) {
        URL jarUrl = connection.getJarFileURL();
        String key = jarUrl.toString();
        Long known = JAR_LAST_MODIFIED.get(key);
        if (known != null) {
            return known;
        }
        long resolved = 0L;
        try {
            resolved = Path.of(jarUrl.toURI()).toFile().lastModified();
        } catch (URISyntaxException | RuntimeException ignored) {
            // a non-file archive (nested or remote) has no mtime to read; 0 drops the header
        }
        JAR_LAST_MODIFIED.putIfAbsent(key, resolved);
        return resolved;
    }

    // A jar connection has opened no stream, so there is nothing to release; a file: connection opened
    // one while reading its metadata, and closing it hands the file handle back.
    private static void release(URLConnection connection) {
        if (connection instanceof JarURLConnection) {
            return;
        }
        try (InputStream ignored = connection.getInputStream()) {
            // closing is the point
        } catch (IOException ignored) {
            // the metadata was already read; failing to release the probe stream is inconsequential
        }
    }

    private record ResourceMeta(long size, long lastModified, String tag) {
    }
}
