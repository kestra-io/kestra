package io.kestra.webserver.filter;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import io.kestra.webserver.services.BasicAuthService;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.GET;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end wiring checks for {@link SecurityHeadersFilter}: confirms the {@code kestra.webserver.security-headers.*}
 * configuration actually binds and the filter is applied to a real HTTP response. Header-value logic itself is
 * covered by the plain unit tests in {@link SecurityHeadersFilterTest}.
 */
@MicronautTest(rebuildContext = true)
class SecurityHeadersFilterIntegrationTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    ApplicationContext applicationContext;

    @Inject
    private BasicAuthService.BasicAuthConfiguration basicAuthConfiguration;

    @Test
    void shouldSetSafeDefaultHeadersOnARealResponse() {
        // Given
        assertThat(applicationContext.containsBean(SecurityHeadersFilter.class)).isTrue();

        // When
        var response = client.toBlocking().exchange(GET("/ping"));

        // Then
        assertThat(response.getHeaders().get("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        assertThat(response.getHeaders().get("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().get("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeaders().contains("Content-Security-Policy")).isFalse();
    }

    @Test
    @Property(name = "kestra.webserver.security-headers.enabled", value = "false")
    void shouldSetNoHeadersWhenFilterIsDisabled() {
        // Given
        assertThat(applicationContext.containsBean(SecurityHeadersFilter.class)).isFalse();

        // When
        var response = client.toBlocking().exchange(GET("/ping"));

        // Then
        assertThat(response.getHeaders().contains("X-Frame-Options")).isFalse();
        assertThat(response.getHeaders().contains("X-Content-Type-Options")).isFalse();
        assertThat(response.getHeaders().contains("Referrer-Policy")).isFalse();
    }

    @Test
    void shouldSetHeadersOnAResponseThatShortCircuitsBeforeTheFilterChainCompletes() {
        // Given - a CSRF-rejected request: CsrfTokenFilter returns 403 without proceeding down the chain,
        // at ServerFilterPhase.SECURITY - if SecurityHeadersFilter ran at a later phase it would never be invoked.
        String basicAuthCookieValue = Base64.getEncoder().encodeToString(
            (basicAuthConfiguration.getUsername() + ":" + basicAuthConfiguration.getPassword()).getBytes()
        );
        MutableHttpRequest<?> request = HttpRequest.POST("/api/v1/main/executions/webhook/unit_test/webhook_test", "")
            .cookie(Cookie.of(BasicAuthService.BASIC_AUTH_COOKIE_NAME, basicAuthCookieValue));

        // When
        HttpClientResponseException exception = null;
        HttpResponse<?> response;
        try {
            response = client.toBlocking().exchange(request);
        } catch (HttpClientResponseException e) {
            exception = e;
            response = e.getResponse();
        }

        // Then
        assertThat(exception).isNotNull();
        assertThat(response.getHeaders().get("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        assertThat(response.getHeaders().get("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().get("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
    }
}
