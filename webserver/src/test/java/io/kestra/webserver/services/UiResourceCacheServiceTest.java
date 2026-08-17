package io.kestra.webserver.services;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import io.kestra.webserver.configuration.UiResourceCacheConfiguration;
import io.kestra.webserver.models.CachedUiResource;
import io.kestra.webserver.utils.UiResourceCompression;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class UiResourceCacheServiceTest {
    private static final MediaType JS = MediaType.of("text/javascript");

    private static UiResourceCacheService service(long maxSize) {
        return new UiResourceCacheService(new UiResourceCacheConfiguration(maxSize));
    }

    private static byte[] compressibleContent() {
        return "this content is highly compressible because it repeats itself over and over again. "
            .repeat(50)
            .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] incompressibleContent(int size) {
        byte[] bytes = new byte[size];
        new Random(42).nextBytes(bytes);
        return bytes;
    }

    private static Supplier<Optional<byte[]>> countingLoader(AtomicInteger counter, byte[] content) {
        return () -> {
            counter.incrementAndGet();
            return Optional.of(content);
        };
    }

    @Test
    void shouldLoadAndCompressOnlyOnceWhenAssetFitsInTheBound() {
        // Given
        UiResourceCacheService service = service(10 * 1024 * 1024);
        AtomicInteger loads = new AtomicInteger();
        byte[] content = compressibleContent();

        // When
        Optional<CachedUiResource> first = service.get("a", JS, countingLoader(loads, content));
        Optional<CachedUiResource> second = service.get("a", JS, countingLoader(loads, content));

        // Then
        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(loads.get()).isEqualTo(1);
        assertThat(first.get().raw()).isEqualTo(content);
        assertThat(first.get().gzip()).isNotNull();
        assertThat(first.get().gzip().length).isLessThan(content.length);
        assertThat(first.get().etagBase()).isNotBlank();
    }

    @Test
    void shouldStillServeButReloadWhenAssetIsLargerThanTheBound() {
        // Given - a bound smaller than the single asset
        UiResourceCacheService service = service(128);
        AtomicInteger loads = new AtomicInteger();
        byte[] content = incompressibleContent(1024);

        // When
        Optional<CachedUiResource> first = service.get("oversized", MediaType.APPLICATION_OCTET_STREAM_TYPE, countingLoader(loads, content));
        Optional<CachedUiResource> second = service.get("oversized", MediaType.APPLICATION_OCTET_STREAM_TYPE, countingLoader(loads, content));

        // Then - both requests are served, but the entry cannot stay resident
        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(loads.get()).isEqualTo(2);
    }

    @Test
    void shouldEvictWhenTotalWeightExceedsTheBound() {
        // Given - two assets that each fit but cannot both stay resident
        UiResourceCacheService service = service(1000);
        AtomicInteger loads = new AtomicInteger();
        byte[] content = incompressibleContent(600);

        // When
        service.get("a", MediaType.APPLICATION_OCTET_STREAM_TYPE, countingLoader(loads, content));
        service.get("b", MediaType.APPLICATION_OCTET_STREAM_TYPE, countingLoader(loads, content));
        service.get("a", MediaType.APPLICATION_OCTET_STREAM_TYPE, countingLoader(loads, content));
        service.get("b", MediaType.APPLICATION_OCTET_STREAM_TYPE, countingLoader(loads, content));

        // Then - at least one of the four gets had to reload after an eviction
        assertThat(loads.get()).isGreaterThan(2);
    }

    @Test
    void shouldSkipPrecompressionWhenContentDoesNotShrink() {
        // Given - random bytes do not compress
        UiResourceCacheService service = service(10 * 1024 * 1024);
        byte[] content = incompressibleContent(2048);

        // When
        Optional<CachedUiResource> asset = service.get("random", MediaType.TEXT_PLAIN_TYPE, () -> Optional.of(content));

        // Then
        assertThat(asset).isPresent();
        assertThat(asset.get().gzip()).isNull();
    }

    @Test
    void shouldSkipPrecompressionForIncompressibleMediaTypes() {
        // Given - compressible bytes but an already-compressed media type
        UiResourceCacheService service = service(10 * 1024 * 1024);

        // When
        Optional<CachedUiResource> asset = service.get("image", MediaType.IMAGE_PNG_TYPE, () -> Optional.of(compressibleContent()));

        // Then
        assertThat(asset).isPresent();
        assertThat(asset.get().gzip()).isNull();
    }

    @Test
    void shouldReturnEmptyWhenLoaderFindsNoResource() {
        // Given
        UiResourceCacheService service = service(10 * 1024 * 1024);

        // When / Then
        assertThat(service.get("missing", JS, Optional::empty)).isEmpty();
    }

    @Test
    void shouldServeIdentityWhenNoAcceptEncoding() {
        // Given
        UiResourceCacheService service = service(10 * 1024 * 1024);
        byte[] content = compressibleContent();
        CachedUiResource asset = service.get("a", JS, () -> Optional.of(content)).orElseThrow();

        // When
        MutableHttpResponse<byte[]> response = service.respond(HttpRequest.GET("/ui/a"), asset, "public, max-age=86400");

        // Then
        assertThat(response.status().getCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(content);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_ENCODING)).isNull();
        assertThat(response.getHeaders().get(HttpHeaders.ETAG)).isEqualTo("\"" + asset.etagBase() + "\"");
        assertThat(response.getHeaders().get(HttpHeaders.VARY)).isEqualTo(HttpHeaders.ACCEPT_ENCODING);
        assertThat(response.getHeaders().get(HttpHeaders.CACHE_CONTROL)).isEqualTo("public, max-age=86400");
        assertThat(response.getContentLength()).isEqualTo(content.length);
    }

    @Test
    void shouldServeGzipWhenAccepted() {
        // Given
        UiResourceCacheService service = service(10 * 1024 * 1024);
        CachedUiResource asset = service.get("a", JS, () -> Optional.of(compressibleContent())).orElseThrow();

        // When
        MutableHttpResponse<byte[]> response = service.respond(
            HttpRequest.GET("/ui/a").header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate"),
            asset,
            "no-cache"
        );

        // Then
        assertThat(response.body()).isEqualTo(asset.gzip());
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_ENCODING)).isEqualTo("gzip");
        assertThat(response.getHeaders().get(HttpHeaders.ETAG)).isEqualTo("\"" + asset.etagBase() + "-gz\"");
        assertThat(response.getContentLength()).isEqualTo(asset.gzip().length);
    }

    @Test
    void shouldPreferBrotliOverGzipWhenBothAcceptedAndAvailable() {
        // Given - a brotli variant crafted directly, so this test does not depend on the brotli4j natives
        byte[] brotli = {1, 2, 3};
        CachedUiResource asset = new CachedUiResource(JS, compressibleContent(), new byte[]{4, 5}, brotli, "etag");
        UiResourceCacheService service = service(1024);

        // When
        MutableHttpResponse<byte[]> response = service.respond(
            HttpRequest.GET("/ui/a").header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br, zstd"),
            asset,
            "no-cache"
        );

        // Then
        assertThat(response.body()).isEqualTo(brotli);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_ENCODING)).isEqualTo("br");
        assertThat(response.getHeaders().get(HttpHeaders.ETAG)).isEqualTo("\"etag-br\"");
    }

    @Test
    void shouldServeIdentityWhenGzipIsRefusedWithZeroQuality() {
        // Given
        UiResourceCacheService service = service(10 * 1024 * 1024);
        byte[] content = compressibleContent();
        CachedUiResource asset = service.get("a", JS, () -> Optional.of(content)).orElseThrow();

        // When
        MutableHttpResponse<byte[]> response = service.respond(
            HttpRequest.GET("/ui/a").header(HttpHeaders.ACCEPT_ENCODING, "gzip;q=0"),
            asset,
            "no-cache"
        );

        // Then
        assertThat(response.body()).isEqualTo(content);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_ENCODING)).isNull();
    }

    @Test
    void shouldAnswer304WhenIfNoneMatchMatchesTheVariantEtag() {
        // Given
        UiResourceCacheService service = service(10 * 1024 * 1024);
        CachedUiResource asset = service.get("a", JS, () -> Optional.of(compressibleContent())).orElseThrow();
        MutableHttpResponse<byte[]> first = service.respond(
            HttpRequest.GET("/ui/a").header(HttpHeaders.ACCEPT_ENCODING, "gzip"),
            asset,
            "public, max-age=31536000, immutable"
        );
        String etag = first.getHeaders().get(HttpHeaders.ETAG);

        // When
        MutableHttpResponse<byte[]> second = service.respond(
            HttpRequest.GET("/ui/a").header(HttpHeaders.ACCEPT_ENCODING, "gzip").header(HttpHeaders.IF_NONE_MATCH, etag),
            asset,
            "public, max-age=31536000, immutable"
        );

        // Then
        assertThat(second.status().getCode()).isEqualTo(HttpStatus.NOT_MODIFIED.getCode());
        assertThat(second.getBody()).isEmpty();
        assertThat(second.getHeaders().get(HttpHeaders.ETAG)).isEqualTo(etag);
        assertThat(second.getHeaders().get(HttpHeaders.CACHE_CONTROL)).isEqualTo("public, max-age=31536000, immutable");
        assertThat(second.getHeaders().get(HttpHeaders.VARY)).isEqualTo(HttpHeaders.ACCEPT_ENCODING);
    }

    @Test
    void shouldAnswer200WhenIfNoneMatchDoesNotMatch() {
        // Given
        UiResourceCacheService service = service(10 * 1024 * 1024);
        CachedUiResource asset = service.get("a", JS, () -> Optional.of(compressibleContent())).orElseThrow();

        // When
        MutableHttpResponse<byte[]> response = service.respond(
            HttpRequest.GET("/ui/a").header(HttpHeaders.IF_NONE_MATCH, "\"another-etag\""),
            asset,
            "no-cache"
        );

        // Then
        assertThat(response.status().getCode()).isEqualTo(200);
        assertThat(response.getBody()).isPresent();
    }

    @Test
    void shouldPrecompressBrotliWhenBrotli4jIsAvailable() throws Exception {
        // Given - only runs when the brotli4j natives resolved for this platform
        assumeTrue(UiResourceCompression.isBrotliAvailable());
        UiResourceCacheService service = service(10 * 1024 * 1024);
        byte[] content = compressibleContent();

        // When
        CachedUiResource asset = service.get("a", JS, () -> Optional.of(content)).orElseThrow();

        // Then - the brotli variant is present, smaller, and round-trips to the raw bytes
        assertThat(asset.brotli()).isNotNull();
        assertThat(asset.brotli().length).isLessThan(content.length);
        byte[] decompressed = com.aayushatharva.brotli4j.decoder.Decoder.decompress(asset.brotli()).getDecompressedData();
        assertThat(decompressed).isEqualTo(content);
    }
}
