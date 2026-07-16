package io.kestra.webserver.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.kestra.webserver.configuration.SecurityHeadersConfiguration;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityHeadersFilterTest {
    private static final SecurityHeadersConfiguration DEFAULT_CONFIGURATION = new SecurityHeadersConfiguration(
        true, "SAMEORIGIN", "nosniff", "strict-origin-when-cross-origin", null, false, null
    );

    @Test
    void shouldSetSafeDefaultHeaders() {
        // Given
        SecurityHeadersFilter filter = new SecurityHeadersFilter(DEFAULT_CONFIGURATION);
        HttpRequest<?> request = request(false);
        MutableHttpResponse<?> response = HttpResponse.ok();

        // When
        filter.addSecurityHeaders(request, response);

        // Then
        assertThat(response.getHeaders().get("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        assertThat(response.getHeaders().get("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().get("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeaders().contains("Content-Security-Policy")).isFalse();
        assertThat(response.getHeaders().contains("Strict-Transport-Security")).isFalse();
    }

    @Test
    void shouldNotOverrideAnAlreadySetHeader() {
        // Given - a controller or another filter already set X-Frame-Options
        SecurityHeadersFilter filter = new SecurityHeadersFilter(DEFAULT_CONFIGURATION);
        HttpRequest<?> request = request(false);
        MutableHttpResponse<?> response = HttpResponse.ok();
        response.getHeaders().set("X-Frame-Options", "DENY");

        // When
        filter.addSecurityHeaders(request, response);

        // Then
        assertThat(response.getHeaders().get("X-Frame-Options")).isEqualTo("DENY");
    }

    @ParameterizedTest
    @CsvSource(
        {
            "false, Content-Security-Policy, Content-Security-Policy-Report-Only",
            "true, Content-Security-Policy-Report-Only, Content-Security-Policy"
        }
    )
    void shouldSetContentSecurityPolicyUnderTheConfiguredHeaderName(boolean reportOnly, String expectedHeader, String unexpectedHeader) {
        // Given
        SecurityHeadersConfiguration configuration = new SecurityHeadersConfiguration(
            true, "SAMEORIGIN", "nosniff", "strict-origin-when-cross-origin", "default-src 'self'", reportOnly, null
        );
        SecurityHeadersFilter filter = new SecurityHeadersFilter(configuration);
        HttpRequest<?> request = request(false);
        MutableHttpResponse<?> response = HttpResponse.ok();

        // When
        filter.addSecurityHeaders(request, response);

        // Then
        assertThat(response.getHeaders().get(expectedHeader)).isEqualTo("default-src 'self'");
        assertThat(response.getHeaders().contains(unexpectedHeader)).isFalse();
    }

    @Test
    void shouldSetStrictTransportSecurityOnlyOnSecureRequest() {
        // Given
        SecurityHeadersConfiguration configuration = new SecurityHeadersConfiguration(
            true, "SAMEORIGIN", "nosniff", "strict-origin-when-cross-origin", null, false, "max-age=31536000; includeSubDomains"
        );
        SecurityHeadersFilter filter = new SecurityHeadersFilter(configuration);

        // When - insecure (plain HTTP) request
        MutableHttpResponse<?> insecureResponse = HttpResponse.ok();
        filter.addSecurityHeaders(request(false), insecureResponse);

        // Then - HSTS must not be advertised over plain HTTP
        assertThat(insecureResponse.getHeaders().contains("Strict-Transport-Security")).isFalse();

        // When - secure (HTTPS) request
        MutableHttpResponse<?> secureResponse = HttpResponse.ok();
        filter.addSecurityHeaders(request(true), secureResponse);

        // Then
        assertThat(secureResponse.getHeaders().get("Strict-Transport-Security")).isEqualTo("max-age=31536000; includeSubDomains");
    }

    @Test
    void shouldSetNoHeadersWhenAllValuesAreBlank() {
        // Given
        SecurityHeadersConfiguration configuration = new SecurityHeadersConfiguration(
            true, "", "", "", null, false, null
        );
        SecurityHeadersFilter filter = new SecurityHeadersFilter(configuration);
        HttpRequest<?> request = request(false);
        MutableHttpResponse<?> response = HttpResponse.ok();

        // When
        filter.addSecurityHeaders(request, response);

        // Then
        assertThat(response.getHeaders().contains("X-Frame-Options")).isFalse();
        assertThat(response.getHeaders().contains("X-Content-Type-Options")).isFalse();
        assertThat(response.getHeaders().contains("Referrer-Policy")).isFalse();
    }

    private static HttpRequest<?> request(boolean secure) {
        HttpRequest<?> request = mock(HttpRequest.class);
        when(request.isSecure()).thenReturn(secure);
        return request;
    }
}
