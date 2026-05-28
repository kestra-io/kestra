package io.kestra.webserver.filter;

import io.kestra.webserver.services.BasicAuthService;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.security.csrf.CsrfConfiguration;
import io.micronaut.security.csrf.generator.CsrfTokenGenerator;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class CsrfTokenFilterTest {
    @Inject
    @Client("/")
    private ReactorHttpClient client;

    @Inject
    private BasicAuthService.BasicAuthConfiguration basicAuthConfiguration;

    @Inject
    private CsrfTokenGenerator<HttpRequest<?>> csrfTokenGenerator;

    @Inject
    private CsrfConfiguration csrfConfiguration;

    private String basicAuthCookieValue() {
        return Base64.getEncoder().encodeToString(
            (basicAuthConfiguration.getUsername() + ":" + basicAuthConfiguration.getPassword()).getBytes()
        );
    }

    @Test
    void shouldRejectPostWithCookieAndNoCsrfToken() {
        // Given - simulates a CSRF attack: browser sends cookie but attacker can't set CSRF header
        MutableHttpRequest<?> request = HttpRequest.POST("/api/v1/main/executions/webhook/unit_test/webhook_test", "")
            .cookie(Cookie.of(BasicAuthService.BASIC_AUTH_COOKIE_NAME, basicAuthCookieValue()));

        // When/Then
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(request)
        );
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.FORBIDDEN.getCode());
    }

    @Test
    void shouldAcceptGetWithAuthorizationHeaderOnly() {
        // Given - SDK/API client path: Authorization header, no cookie, no CSRF
        var response = client.toBlocking()
            .exchange(HttpRequest.GET("/api/v1/configs")
                .basicAuth(basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword()));

        // Then
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void shouldAcceptGetWithCookieAndNoCsrfToken() {
        // Given - GET is a safe method, no CSRF needed even with cookie
        var response = client.toBlocking()
            .exchange(HttpRequest.GET("/api/v1/configs")
                .cookie(Cookie.of(BasicAuthService.BASIC_AUTH_COOKIE_NAME, basicAuthCookieValue())));

        // Then
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void shouldAcceptPostWithCookieAndValidCsrfToken() {
        // Given - browser path with valid CSRF token
        MutableHttpRequest<?> tokenRequest = HttpRequest.GET("/api/v1/configs");
        String csrfToken = csrfTokenGenerator.generateCsrfToken(tokenRequest);

        MutableHttpRequest<?> request = HttpRequest.POST("/api/v1/main/executions/webhook/unit_test/webhook_test", "")
            .cookie(Cookie.of(BasicAuthService.BASIC_AUTH_COOKIE_NAME, basicAuthCookieValue()))
            .cookie(Cookie.of(csrfConfiguration.getCookieName(), csrfToken))
            .header("X-CSRF-TOKEN", csrfToken);

        // When/Then - should not be rejected by CSRF filter
        try {
            var response = client.toBlocking().exchange(request);
            assertThat(response.getStatus().getCode()).isNotEqualTo(HttpStatus.FORBIDDEN.getCode());
        } catch (HttpClientResponseException e) {
            assertThat(e.getStatus().getCode()).isNotEqualTo(HttpStatus.FORBIDDEN.getCode());
        }
    }

    @Test
    void shouldRejectPostWithCookieAndInvalidCsrfToken() {
        // Given - browser path with an invalid CSRF token
        MutableHttpRequest<?> request = HttpRequest.POST("/api/v1/main/executions/webhook/unit_test/webhook_test", "")
            .cookie(Cookie.of(BasicAuthService.BASIC_AUTH_COOKIE_NAME, basicAuthCookieValue()))
            .cookie(Cookie.of(csrfConfiguration.getCookieName(), "invalid-token"))
            .header("X-CSRF-TOKEN", "invalid-token");

        // When/Then
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(request)
        );
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.FORBIDDEN.getCode());
    }
}
