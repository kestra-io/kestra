package io.kestra.webserver.controllers.api;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;

import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests of the UI asset serving path, using {@link HttpClient} because it neither injects
 * {@code Accept-Encoding} headers nor transparently decompresses responses.
 */
@KestraTest
class UiControllerTest {
    private static final String FIXTURE_ASSET = "/ui/assets/asset-fixture-abc123.js";

    @Inject
    EmbeddedServer embeddedServer;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(embeddedServer.getURL().toString() + path));
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldServeGzipVariantWithImmutableCachingForHashedAssets() throws Exception {
        // When
        HttpResponse<byte[]> response = send(request(FIXTURE_ASSET).header("Accept-Encoding", "gzip, deflate").build());

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).contains("gzip");
        assertThat(response.headers().firstValue("Vary")).contains("Accept-Encoding");
        assertThat(response.headers().firstValue("Cache-Control")).contains("public, max-age=31536000, immutable");
        assertThat(response.headers().firstValue("ETag").orElseThrow()).endsWith("-gz\"");
        assertThat(Long.parseLong(response.headers().firstValue("Content-Length").orElseThrow())).isEqualTo(response.body().length);

        byte[] decompressed = new GZIPInputStream(new ByteArrayInputStream(response.body())).readAllBytes();
        assertThat(new String(decompressed, StandardCharsets.UTF_8)).contains("fixtureLine60");
    }

    @Test
    void shouldPreferBrotliOverGzipWhenBothAccepted() {
        // When
        HttpResponse<byte[]> response = send(request(FIXTURE_ASSET).header("Accept-Encoding", "gzip, deflate, br, zstd").build());

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).contains("br");
        assertThat(response.headers().firstValue("ETag").orElseThrow()).endsWith("-br\"");
        assertThat(response.headers().firstValue("Cache-Control")).contains("immutable");
    }

    @Test
    void shouldServeIdentityWhenClientDoesNotAcceptCompression() {
        // When
        HttpResponse<byte[]> response = send(request(FIXTURE_ASSET).build());

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(response.headers().firstValue("ETag").orElseThrow()).doesNotEndWith("-gz\"");
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("fixtureLine60");
    }

    @Test
    void shouldAnswer304WithoutBodyWhenIfNoneMatchMatches() {
        // Given
        HttpResponse<byte[]> first = send(request(FIXTURE_ASSET).header("Accept-Encoding", "gzip").build());
        String etag = first.headers().firstValue("ETag").orElseThrow();

        // When
        HttpResponse<byte[]> second = send(
            request(FIXTURE_ASSET).header("Accept-Encoding", "gzip").header("If-None-Match", etag).build()
        );

        // Then
        assertThat(second.statusCode()).isEqualTo(304);
        assertThat(second.body()).isEmpty();
        assertThat(second.headers().firstValue("ETag")).contains(etag);
        assertThat(second.headers().firstValue("Cache-Control")).contains("public, max-age=31536000, immutable");
        assertThat(second.headers().firstValue("Vary")).contains("Accept-Encoding");
    }

    @Test
    void shouldApplyDefaultCachePolicyForNonHashedFiles() {
        // When
        HttpResponse<byte[]> response = send(request("/ui/asset-fixture.txt").build());

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("public, max-age=86400");
        assertThat(response.headers().firstValue("ETag")).isPresent();
    }

    @Test
    void shouldServeRewrittenIndexWithNoCacheForTheUiRoot() {
        // When
        HttpResponse<byte[]> response = send(request("/ui/").build());

        // Then
        String html = new String(response.body(), StandardCharsets.UTF_8);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("text/html");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-cache");
        assertThat(html).contains("src=\"/ui/assets/asset-fixture-abc123.js\"");
        assertThat(html).contains("<meta name=\"csrf-token\" content=\"");
        assertThat(response.headers().firstValue("Set-Cookie")).isPresent();
        assertThat(Long.parseLong(response.headers().firstValue("Content-Length").orElseThrow())).isEqualTo(response.body().length);
    }

    @Test
    void shouldFallbackToIndexForSpaHistoryModeRoutes() {
        // When - a deep link that matches no static asset
        HttpResponse<byte[]> response = send(request("/ui/main/flows/edit/io.kestra.tests/some-flow").build());

        // Then
        String html = new String(response.body(), StandardCharsets.UTF_8);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("text/html");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-cache");
        assertThat(html).contains("<div id=\"app\"></div>");
    }

    @Test
    void shouldAnswer304ForIndexWhenTokenAndContentAreStable() {
        // Given - reuse the CSRF cookie so the rendered body is identical
        HttpResponse<byte[]> first = send(request("/ui/").build());
        String etag = first.headers().firstValue("ETag").orElseThrow();
        String setCookie = first.headers().firstValue("Set-Cookie").orElseThrow();
        String cookiePair = setCookie.substring(0, setCookie.indexOf(';'));

        // When
        HttpResponse<byte[]> second = send(
            request("/ui/").header("Cookie", cookiePair).header("If-None-Match", etag).build()
        );

        // Then
        assertThat(second.statusCode()).isEqualTo(304);
        assertThat(second.body()).isEmpty();
    }

    @Test
    void shouldRejectPathTraversal() {
        // When - the encoded dots decode to a traversal attempt
        HttpResponse<byte[]> response = send(request("/ui/assets/%2e%2e/%2e%2e/application-test.yml").build());

        // Then
        assertThat(response.statusCode()).isEqualTo(404);
    }
}
