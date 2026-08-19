package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.JarEntry;

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
 * Serves the UI single-page application without holding any bytes in memory: the UI build emits
 * precompressed {@code .gz}/{@code .br} siblings for every compressible file, so a request streams
 * the negotiated variant straight from the classpath with an entity tag derived from the zip entry
 * metadata — no request ever inflates or compresses content. Unresolved paths fall back to the
 * rewritten {@code index.html}
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
    private static final String GZIP = "gzip";
    private static final String BROTLI = "br";

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
        if (normalized.contains("..") || normalized.contains("\\") || normalized.contains("\0")) {
            return HttpResponse.notFound();
        }

        // Only paths whose last segment has an extension can be static resources; SPA routes never resolve here.
        if (isStaticResourceCandidate(normalized) && !INDEX_HTML.equals(normalized)) {
            URL identityUrl = UiController.class.getClassLoader().getResource("ui/" + normalized);
            if (identityUrl != null) {
                return respond(request, normalized, identityUrl);
            }
        }

        // SPA history-mode fallback: any unresolved /ui/** path serves the rewritten index.html.
        Optional<? extends HttpResponse<?>> index = uiIndexService.render(request);
        return index.isPresent() ? index.get() : HttpResponse.notFound();
    }

    private HttpResponse<?> respond(HttpRequest<?> request, String normalized, URL identityUrl) {
        MediaType mediaType = MediaType
            .forExtension(NameUtils.extension(normalized))
            .orElse(MediaType.APPLICATION_OCTET_STREAM_TYPE);
        String cacheControl = normalized.startsWith(HASHED_ASSETS_PREFIX) ? HASHED_ASSETS_CACHE_CONTROL : DEFAULT_CACHE_CONTROL;

        String acceptEncoding = request.getHeaders().get(HttpHeaders.ACCEPT_ENCODING);
        String contentEncoding = null;
        URL variantUrl = identityUrl;
        if (HttpCacheUtils.accepts(acceptEncoding, BROTLI)) {
            URL brotli = UiController.class.getClassLoader().getResource("ui/" + normalized + ".br");
            if (brotli != null) {
                variantUrl = brotli;
                contentEncoding = BROTLI;
            }
        }
        if (contentEncoding == null && HttpCacheUtils.accepts(acceptEncoding, GZIP)) {
            URL gzip = UiController.class.getClassLoader().getResource("ui/" + normalized + ".gz");
            if (gzip != null) {
                variantUrl = gzip;
                contentEncoding = GZIP;
            }
        }

        // The entity tag comes from the identity entry so it names the content, not the coding;
        // the served variant only adds the coding suffix.
        ResourceMeta identityMeta = metaOf(identityUrl);
        String etag = identityMeta == null ? null : HttpCacheUtils.etagFor(identityMeta.tag(), contentEncoding);

        if (etag != null && HttpCacheUtils.anyEtagMatches(request.getHeaders().get(HttpHeaders.IF_NONE_MATCH), etag)) {
            return applyHeaders(HttpResponse.notModified(), etag, cacheControl, null);
        }

        ResourceMeta variantMeta = variantUrl == identityUrl ? identityMeta : metaOf(variantUrl);
        StreamedFile body = variantMeta == null
            ? new StreamedFile(openStream(variantUrl), mediaType)
            : new StreamedFile(openStream(variantUrl), mediaType, variantMeta.lastModified(), variantMeta.size());
        return applyHeaders(HttpResponse.ok(body), etag, cacheControl, contentEncoding);
    }

    private static <T> MutableHttpResponse<T> applyHeaders(MutableHttpResponse<T> response, @Nullable String etag, String cacheControl, @Nullable String contentEncoding) {
        response
            .header(HttpHeaders.CACHE_CONTROL, cacheControl)
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
        if (etag != null) {
            response.header(HttpHeaders.ETAG, etag);
        }
        if (contentEncoding != null) {
            response.header(HttpHeaders.CONTENT_ENCODING, contentEncoding);
        }
        return response;
    }

    private static boolean isStaticResourceCandidate(String path) {
        if (path.isEmpty() || path.endsWith("/")) {
            return false;
        }
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        return lastSegment.indexOf('.') > 0;
    }

    private static InputStream openStream(URL url) {
        try {
            return url.openStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Resolves size, last-modified and a content-identifying tag from the resource's metadata alone:
     * the zip entry CRC for jar resources (entry timestamps are constant in reproducible builds, so
     * they cannot identify content), length and mtime for plain files. Returns null when metadata
     * cannot be read; the caller then streams without validators.
     */
    @Nullable
    private static ResourceMeta metaOf(URL url) {
        try {
            URLConnection connection = url.openConnection();
            if (connection instanceof JarURLConnection jarConnection) {
                JarEntry entry = jarConnection.getJarEntry();
                if (entry == null) {
                    return null;
                }
                return new ResourceMeta(entry.getSize(), entry.getTime(), Long.toHexString(entry.getCrc()) + "-" + Long.toHexString(entry.getSize()));
            }
            long length = connection.getContentLengthLong();
            long lastModified = connection.getLastModified();
            closeQuietly(connection);
            return new ResourceMeta(length, lastModified, Long.toHexString(length) + "-" + Long.toHexString(lastModified));
        } catch (IOException e) {
            return null;
        }
    }

    // URLConnection has no close(); dropping the stream releases the underlying file handle for file: URLs.
    private static void closeQuietly(URLConnection connection) {
        try {
            connection.getInputStream().close();
        } catch (IOException ignored) {
            // metadata was already read; a failure to release the probe stream is inconsequential
        }
    }

    private record ResourceMeta(long size, long lastModified, String tag) {
    }
}
