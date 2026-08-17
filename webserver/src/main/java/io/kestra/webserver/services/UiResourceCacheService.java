package io.kestra.webserver.services;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.Deflater;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.kestra.webserver.configuration.UiResourceCacheConfiguration;
import io.kestra.webserver.models.CachedUiResource;
import io.kestra.webserver.utils.UiResourceCompression;
import io.kestra.webserver.utils.HttpCacheUtils;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Bounded in-memory cache of static UI resources (main UI and plugin UI), each entry holding the raw bytes,
 * precompressed gzip/brotli variants, a strong ETag and the media type. Resources are loaded and compressed
 * once, off the Netty event loop, then served from memory with {@code Accept-Encoding} negotiation and
 * {@code If-None-Match} handling that never re-reads the jar.
 */
@Singleton
public class UiResourceCacheService {
    private static final int PRECOMPRESS_MIN_SIZE = 256;
    private static final double PRECOMPRESS_MAX_RATIO = 0.9d;
    private static final String GZIP = "gzip";
    private static final String BROTLI = "br";
    private static final Set<String> INCOMPRESSIBLE_MEDIA_TYPES = Set.of(
        "image/png",
        "image/jpeg",
        "image/gif",
        "image/webp",
        "image/avif",
        "font/woff",
        "font/woff2",
        "application/font-woff",
        "application/zip",
        "application/gzip",
        "application/x-gzip"
    );

    private final Cache<String, CachedUiResource> cache;

    @Inject
    public UiResourceCacheService(UiResourceCacheConfiguration configuration) {
        long maxSize = configuration.maxSize();
        this.cache = Caffeine.newBuilder()
            .maximumWeight(maxSize)
            .weigher((String key, CachedUiResource resource) -> (int) Math.min(Integer.MAX_VALUE, resource.weight() + key.length()))
            // Run eviction inline so the cache never exceeds its bound between maintenance cycles.
            .executor(Runnable::run)
            .build();
    }

    /**
     * Returns the cached UI resource for the given key, loading and precompressing it once on miss.
     *
     * @param key       the cache key, unique per servable resource version.
     * @param mediaType the content type of the resource.
     * @param loader    reads the raw bytes on cache miss; empty when the resource does not exist.
     * @return the cached resource, or empty when the loader found no resource.
     */
    public Optional<CachedUiResource> get(String key, MediaType mediaType, Supplier<Optional<byte[]>> loader) {
        return Optional.ofNullable(cache.get(key, k -> loader.get().map(raw -> build(mediaType, raw)).orElse(null)));
    }

    /**
     * Builds the full response for a cached UI resource: content-coding negotiation via {@code Accept-Encoding},
     * {@code Vary}, a strong per-variant ETag, and a 304 when {@code If-None-Match} matches.
     */
    public MutableHttpResponse<byte[]> respond(HttpRequest<?> request, CachedUiResource resource, String cacheControl) {
        String acceptEncoding = request.getHeaders().get(HttpHeaders.ACCEPT_ENCODING);

        byte[] body = resource.raw();
        String contentEncoding = null;
        if (resource.brotli() != null && HttpCacheUtils.accepts(acceptEncoding, BROTLI)) {
            body = resource.brotli();
            contentEncoding = BROTLI;
        } else if (resource.gzip() != null && HttpCacheUtils.accepts(acceptEncoding, GZIP)) {
            body = resource.gzip();
            contentEncoding = GZIP;
        }

        String etag = etagFor(resource.etagBase(), contentEncoding);
        if (HttpCacheUtils.anyEtagMatches(request.getHeaders().get(HttpHeaders.IF_NONE_MATCH), etag)) {
            return applyCacheHeaders(HttpResponse.notModified(), etag, cacheControl);
        }

        MutableHttpResponse<byte[]> response = applyCacheHeaders(HttpResponse.ok(body), etag, cacheControl)
            .contentType(resource.mediaType())
            .contentLength(body.length);
        if (contentEncoding != null) {
            response.header(HttpHeaders.CONTENT_ENCODING, contentEncoding);
        }
        return response;
    }

    /**
     * @return the strong entity tag for a content-coding variant of an resource.
     */
    public static String etagFor(String etagBase, @Nullable String contentEncoding) {
        String suffix = contentEncoding == null ? "" : "-" + (BROTLI.equals(contentEncoding) ? "br" : "gz");
        return "\"" + etagBase + suffix + "\"";
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static <T> MutableHttpResponse<T> applyCacheHeaders(MutableHttpResponse<T> response, String etag, String cacheControl) {
        return response
            .header(HttpHeaders.ETAG, etag)
            .header(HttpHeaders.CACHE_CONTROL, cacheControl)
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
    }

    private static CachedUiResource build(MediaType mediaType, byte[] raw) {
        byte[] gzip = null;
        byte[] brotli = null;
        if (raw.length >= PRECOMPRESS_MIN_SIZE && isCompressible(mediaType)) {
            long maxCompressedSize = (long) (raw.length * PRECOMPRESS_MAX_RATIO);

            byte[] gzipped = UiResourceCompression.gzip(raw, Deflater.BEST_COMPRESSION);
            if (gzipped.length <= maxCompressedSize) {
                gzip = gzipped;
            }

            byte[] brotlied = UiResourceCompression.brotli(raw);
            if (brotlied != null && brotlied.length <= maxCompressedSize) {
                brotli = brotlied;
            }
        }
        return new CachedUiResource(mediaType, raw, gzip, brotli, sha256Hex(raw));
    }

    private static boolean isCompressible(MediaType mediaType) {
        String name = mediaType.getName().toLowerCase(Locale.ROOT);
        return !name.startsWith("video/")
            && !name.startsWith("audio/")
            && !INCOMPRESSIBLE_MEDIA_TYPES.contains(name);
    }
}
