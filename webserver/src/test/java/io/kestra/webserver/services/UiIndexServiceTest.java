package io.kestra.webserver.services;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.webserver.configuration.WebserverConfiguration;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.simple.SimpleHttpRequest;
import io.micronaut.security.csrf.CsrfConfiguration;
import io.micronaut.security.csrf.generator.CsrfTokenGenerator;
import io.micronaut.security.csrf.validator.CsrfTokenValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiIndexServiceTest {
    private static final String COOKIE_NAME = "CSRF-TOKEN";

    private static UiIndexService service(String basePath, WebserverConfiguration configuration) {
        return new UiIndexService(basePath, configuration, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static CsrfConfiguration csrfConfiguration() {
        CsrfConfiguration csrfConfiguration = mock(CsrfConfiguration.class);
        when(csrfConfiguration.getCookieName()).thenReturn(COOKIE_NAME);
        return csrfConfiguration;
    }

    @SuppressWarnings("unchecked")
    private static CsrfTokenValidator<HttpRequest<?>> validatorAccepting(boolean valid) {
        CsrfTokenValidator<HttpRequest<?>> validator = mock(CsrfTokenValidator.class);
        when(validator.validateCsrfToken(any(), any())).thenReturn(valid);
        return validator;
    }

    private static WebserverConfiguration emptyConfiguration() {
        return new WebserverConfiguration(null, null, null);
    }

    // SimpleHttpRequest, unlike the client request from HttpRequest.GET(), implements getCookies().
    private static MutableHttpRequest<?> get(String uri) {
        return new SimpleHttpRequest<>(HttpMethod.GET, uri, null);
    }

    private static String body(MutableHttpResponse<byte[]> response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    @Test
    void shouldRewriteRelativePathsToTheUiBasePathOnce() {
        // Given
        UiIndexService service = service(null, emptyConfiguration());

        // When
        MutableHttpResponse<byte[]> response = service.render(get("/ui/")).orElseThrow();

        // Then
        assertThat(response.status().getCode()).isEqualTo(200);
        assertThat(body(response)).contains("src=\"/ui/assets/asset-fixture-abc123.js\"");
        assertThat(body(response)).contains("window.KESTRA_UI_PATH = \"/ui/\";");
        assertThat(response.getHeaders().get(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-cache, private");
        // the body carries the user's CSRF token, so it must never be revalidatable
        assertThat(response.getHeaders().get(HttpHeaders.ETAG)).isNull();
        assertThat(response.getContentLength()).isEqualTo(response.body().length);
    }

    @Test
    void shouldPrefixWithContextPathWhenConfigured() {
        // Given
        UiIndexService service = service("/kestra", emptyConfiguration());

        // When
        MutableHttpResponse<byte[]> response = service.render(get("/kestra/ui/")).orElseThrow();

        // Then
        assertThat(body(response)).contains("src=\"/kestra/ui/assets/asset-fixture-abc123.js\"");
    }

    @Test
    void shouldApplyGoogleAnalyticsTitleAndHtmlHeadReplacements() {
        // Given
        UiIndexService service = service(null, new WebserverConfiguration("GA-123", "My Kestra", "<meta name=\"custom\" content=\"here\">"));

        // When
        String html = body(service.render(get("/ui/")).orElseThrow());

        // Then
        assertThat(html).contains("KESTRA_GOOGLE_ANALYTICS = 'GA-123';");
        assertThat(html).contains("<title>My Kestra</title>");
        assertThat(html).contains("<meta name=\"custom\" content=\"here\">");
        assertThat(html).doesNotContain("<meta name=\"html-head\" content=\"replace\">");
    }

    @Test
    void shouldUseUtf8ByteLengthForContentLengthWhenHtmlHeadContainsNonAscii() {
        // Given - a html-head whose UTF-8 byte length differs from its character count
        String htmlHead = "<meta name=\"description\" content=\"héllo wörld — 日本語\">";
        UiIndexService service = service(null, new WebserverConfiguration(null, null, htmlHead));

        // When
        MutableHttpResponse<byte[]> response = service.render(get("/ui/")).orElseThrow();

        // Then
        String html = body(response);
        assertThat(html).contains(htmlHead);
        assertThat(response.body().length).isGreaterThan(html.length());
        assertThat(response.getContentLength()).isEqualTo(response.body().length);
    }

    @Test
    void shouldInsertEscapedCsrfMetaAndCookieWhenGeneratorIsPresent() {
        // Given
        @SuppressWarnings("unchecked")
        CsrfTokenGenerator<HttpRequest<?>> generator = mock(CsrfTokenGenerator.class);
        when(generator.generateCsrfToken(any())).thenReturn("to<k>&en");
        UiIndexService service = new UiIndexService(null, emptyConfiguration(), Optional.of(csrfConfiguration()), Optional.of(generator), Optional.empty());

        // When
        MutableHttpResponse<byte[]> response = service.render(get("/ui/")).orElseThrow();

        // Then
        assertThat(body(response)).contains("<head>\n<meta name=\"csrf-token\" content=\"to&lt;k&gt;&amp;en\">");
        assertThat(response.getCookies().findCookie(COOKIE_NAME).map(Cookie::getValue)).contains("to<k>&en");
    }

    @Test
    void shouldInsertExactlyOneCsrfMetaTagPerRender() {
        // Given
        @SuppressWarnings("unchecked")
        CsrfTokenGenerator<HttpRequest<?>> generator = mock(CsrfTokenGenerator.class);
        when(generator.generateCsrfToken(any())).thenReturn("token");
        UiIndexService service = new UiIndexService(null, emptyConfiguration(), Optional.of(csrfConfiguration()), Optional.of(generator), Optional.empty());

        // When - the template is rendered twice from the same immutable source
        service.render(get("/ui/")).orElseThrow();
        String html = body(service.render(get("/ui/")).orElseThrow());

        // Then - a stale token from the previous render never accumulates in the template
        assertThat(html.split("<meta name=\"csrf-token\"", -1)).hasSize(2);
        verify(generator, times(2)).generateCsrfToken(any());
    }

    @Test
    void shouldReuseTokenFromRequestCookieWithoutGeneratingANewOne() {
        // Given
        @SuppressWarnings("unchecked")
        CsrfTokenGenerator<HttpRequest<?>> generator = mock(CsrfTokenGenerator.class);
        CsrfTokenValidator<HttpRequest<?>> validator = validatorAccepting(true);
        UiIndexService service = new UiIndexService(null, emptyConfiguration(), Optional.of(csrfConfiguration()), Optional.of(generator), Optional.of(validator));

        // When
        MutableHttpResponse<byte[]> response = service
            .render(get("/ui/").cookie(Cookie.of(COOKIE_NAME, "existing-token")))
            .orElseThrow();

        // Then
        assertThat(body(response)).contains("<meta name=\"csrf-token\" content=\"existing-token\">");
        assertThat(response.getCookies().findCookie(COOKIE_NAME).map(Cookie::getValue)).contains("existing-token");
        verify(validator).validateCsrfToken(any(), eq("existing-token"));
        verify(generator, never()).generateCsrfToken(any());
    }

    @Test
    void shouldReuseTokenFromRequestCookieWhenNoValidatorIsAvailable() {
        // Given - the check is skipped when no validator bean exists
        @SuppressWarnings("unchecked")
        CsrfTokenGenerator<HttpRequest<?>> generator = mock(CsrfTokenGenerator.class);
        UiIndexService service = new UiIndexService(null, emptyConfiguration(), Optional.of(csrfConfiguration()), Optional.of(generator), Optional.empty());

        // When
        MutableHttpResponse<byte[]> response = service
            .render(get("/ui/").cookie(Cookie.of(COOKIE_NAME, "existing-token")))
            .orElseThrow();

        // Then
        assertThat(body(response)).contains("<meta name=\"csrf-token\" content=\"existing-token\">");
        verify(generator, never()).generateCsrfToken(any());
    }

    @Test
    void shouldReplaceACookieTokenThisInstanceCannotValidate() {
        // Given - a cookie left behind by another instance on the same host (different signature key)
        @SuppressWarnings("unchecked")
        CsrfTokenGenerator<HttpRequest<?>> generator = mock(CsrfTokenGenerator.class);
        when(generator.generateCsrfToken(any())).thenReturn("fresh-token");
        CsrfTokenValidator<HttpRequest<?>> validator = validatorAccepting(false);
        UiIndexService service = new UiIndexService(null, emptyConfiguration(), Optional.of(csrfConfiguration()), Optional.of(generator), Optional.of(validator));

        // When
        MutableHttpResponse<byte[]> response = service
            .render(get("/ui/").cookie(Cookie.of(COOKIE_NAME, "stale-token")))
            .orElseThrow();

        // Then - the page and the cookie both carry a token this instance will accept
        assertThat(body(response)).contains("<meta name=\"csrf-token\" content=\"fresh-token\">");
        assertThat(body(response)).doesNotContain("stale-token");
        assertThat(response.getCookies().findCookie(COOKIE_NAME).map(Cookie::getValue)).contains("fresh-token");
        verify(validator).validateCsrfToken(any(), eq("stale-token"));
    }
}
