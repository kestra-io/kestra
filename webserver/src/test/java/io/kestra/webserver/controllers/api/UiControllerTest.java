package io.kestra.webserver.controllers.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.junit.annotations.KestraTest;

import io.micronaut.http.MediaType;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.security.csrf.CsrfConfiguration;
import io.micronaut.security.csrf.generator.CsrfTokenGenerator;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests of the UI asset serving path, using {@link HttpClient} because it neither injects
 * {@code Accept-Encoding} headers nor transparently decompresses responses.
 */
@KestraTest
class UiControllerTest {
    private static final String FIXTURE_ASSET = "/ui/assets/asset-fixture-abc123.js";
    private static final Pattern CSRF_META = Pattern.compile("<meta name=\"csrf-token\" content=\"([^\"]*)\">");

    @Inject
    EmbeddedServer embeddedServer;

    @Inject
    CsrfConfiguration csrfConfiguration;

    @Inject
    CsrfTokenGenerator<io.micronaut.http.HttpRequest<?>> csrfTokenGenerator;

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
    void shouldReuseACsrfCookieTokenThisInstanceIssued() {
        // Given
        String token = csrfTokenGenerator.generateCsrfToken(io.micronaut.http.HttpRequest.GET("/ui/"));

        // When
        HttpResponse<byte[]> response = send(request("/ui/").header("Cookie", csrfConfiguration.getCookieName() + "=" + token).build());

        // Then
        assertThat(csrfMetaToken(response)).isEqualTo(token);
        assertThat(response.headers().firstValue("Set-Cookie").orElseThrow()).startsWith(csrfConfiguration.getCookieName() + "=" + token);
    }

    @Test
    void shouldReplaceACsrfCookieTokenSignedByAnotherInstance() {
        // Given - a well-formed token whose signature no longer matches this instance's key
        // (an OSS instance replaced by EE on the same host, a rotated encryption secret key)
        String stale = "c3RhbGVTaWduYXR1cmU.c3RhbGVSYW5kb20";

        // When
        HttpResponse<byte[]> response = send(request("/ui/").header("Cookie", csrfConfiguration.getCookieName() + "=" + stale).build());

        // Then - the page carries a fresh token and the cookie is rewritten to the same value
        String fresh = csrfMetaToken(response);
        assertThat(fresh).isNotEqualTo(stale);
        assertThat(response.headers().firstValue("Set-Cookie").orElseThrow()).startsWith(csrfConfiguration.getCookieName() + "=" + fresh);
    }

    private static String csrfMetaToken(HttpResponse<byte[]> response) {
        Matcher matcher = CSRF_META.matcher(new String(response.body(), StandardCharsets.UTF_8));
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
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
        // Every extension the UI build emits. A resource served as application/octet-stream instead of
        // its real type is fatal for a module script, a stylesheet or the PWA manifest, and invisible
        // until the app fails to boot in a browser.
        strings = { "js", "mjs", "css", "html", "png", "svg", "ico", "woff2", "ttf", "webmanifest" }
    )
    void shouldResolveAMediaTypeForEveryExtensionTheUiShips(String extension) {
        assertThat(UiController.mediaTypeFor("file." + extension))
            .isNotEqualTo(MediaType.APPLICATION_OCTET_STREAM_TYPE);
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
