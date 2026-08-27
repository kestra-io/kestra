package io.kestra.webserver.controllers.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    void shouldServeHashedAssetsWithImmutableCachingAndAnEntityTag() {
        // When
        HttpResponse<byte[]> response = send(request(FIXTURE_ASSET).build());

        // Then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("public, max-age=31536000, immutable");
        assertThat(response.headers().firstValue("Vary")).contains("Accept-Encoding");
        assertThat(response.headers().firstValue("ETag")).isPresent();
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("asset-fixture-body");
    }

    @Test
    void shouldAnswer304WithoutBodyWhenIfNoneMatchMatches() {
        // Given
        HttpResponse<byte[]> first = send(request(FIXTURE_ASSET).build());
        String etag = first.headers().firstValue("ETag").orElseThrow();

        // When
        HttpResponse<byte[]> second = send(request(FIXTURE_ASSET).header("If-None-Match", etag).build());

        // Then
        assertThat(second.statusCode()).isEqualTo(304);
        assertThat(second.body()).isEmpty();
        assertThat(second.headers().firstValue("ETag")).contains(etag);
        assertThat(second.headers().firstValue("Cache-Control")).contains("public, max-age=31536000, immutable");
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
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-cache, private");
        assertThat(html).contains("src=\"/ui/assets/asset-fixture-abc123.js\"");
        assertThat(html).contains("<meta name=\"csrf-token\" content=\"");
        assertThat(response.headers().firstValue("Set-Cookie")).isPresent();
        assertThat(Long.parseLong(response.headers().firstValue("Content-Length").orElseThrow())).isEqualTo(response.body().length);
    }

    @Test
    void shouldServeASingleCsrfTokenPerIndexResponse() {
        // When
        HttpResponse<byte[]> response = send(request("/ui/").build());

        // Then - two filters used to run on /ui/**, injecting two metas and two mismatched cookies
        String html = new String(response.body(), StandardCharsets.UTF_8);
        assertThat(html.split("<meta name=\"csrf-token\"", -1)).hasSize(2);
        assertThat(response.headers().allValues("Set-Cookie")).hasSize(1);
    }

    @Test
    void shouldFallbackToIndexForSpaHistoryModeRoutes() {
        // When - a deep link that matches no static asset
        HttpResponse<byte[]> response = send(request("/ui/main/flows/edit/io.kestra.tests/some-flow").build());

        // Then
        String html = new String(response.body(), StandardCharsets.UTF_8);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("text/html");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-cache, private");
        assertThat(html).contains("<div id=\"app\"></div>");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            // encoded dots decode to a traversal attempt
            "/ui/assets/%2e%2e/%2e%2e/application-test.yml",
            "/ui/assets/../../application-test.yml",
            // Windows-style separators must not slip past the guard either
            "/ui/assets/..%5C..%5Capplication-test.yml",
        }
    )
    void shouldRejectPathTraversal(String uri) {
        // When
        HttpResponse<byte[]> response = send(request(uri).build());

        // Then - never the traversed file, and never the SPA fallback
        assertThat(response.statusCode()).isEqualTo(404);
    }
}
