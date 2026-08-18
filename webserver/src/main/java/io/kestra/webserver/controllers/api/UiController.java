package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;

import io.kestra.webserver.models.CachedUiResource;
import io.kestra.webserver.services.UiResourceCacheService;
import io.kestra.webserver.services.UiIndexService;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.inject.Inject;

/**
 * Serves the UI single-page application from the bounded in-memory {@link UiResourceCacheService}: hashed files under {@code assets/}
 * with immutable cache headers, everything else falling back to the rewritten {@code index.html}
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

    private final UiResourceCacheService uiResourceCacheService;
    private final UiIndexService uiIndexService;

    @Inject
    public UiController(UiResourceCacheService uiResourceCacheService, UiIndexService uiIndexService) {
        this.uiResourceCacheService = Objects.requireNonNull(uiResourceCacheService);
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
            URL resourceUrl = UiController.class.getClassLoader().getResource("ui/" + normalized);
            if (resourceUrl != null) {
                MediaType mediaType = MediaType
                    .forExtension(NameUtils.extension(normalized))
                    .orElse(MediaType.APPLICATION_OCTET_STREAM_TYPE);
                String cacheControl = normalized.startsWith(HASHED_ASSETS_PREFIX) ? HASHED_ASSETS_CACHE_CONTROL : DEFAULT_CACHE_CONTROL;

                if (!uiResourceCacheService.isCacheable(UiResourceCacheService.resourceSize(resourceUrl))) {
                    // Files above the per-entry bound are streamed straight from the jar instead of being buffered.
                    return HttpResponse.ok(new StreamedFile(openStream(resourceUrl), mediaType))
                        .header(HttpHeaders.CACHE_CONTROL, cacheControl);
                }

                Optional<CachedUiResource> resource = uiResourceCacheService.get(
                    "ui:" + normalized,
                    mediaType,
                    () -> Optional.of(UiResourceCacheService.readResource(resourceUrl))
                );
                if (resource.isPresent()) {
                    return uiResourceCacheService.respond(request, resource.get(), cacheControl);
                }
            }
        }

        // SPA history-mode fallback: any unresolved /ui/** path serves the rewritten index.html.
        Optional<? extends HttpResponse<?>> index = uiIndexService.render(request);
        return index.isPresent() ? index.get() : HttpResponse.notFound();
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
}
