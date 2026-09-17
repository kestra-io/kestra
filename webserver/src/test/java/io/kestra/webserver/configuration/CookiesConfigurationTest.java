package io.kestra.webserver.configuration;

import org.junit.jupiter.api.Test;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CookiesConfigurationTest {
    @Test
    void shouldAutoDetectWhenSecureIsUnset() {
        CookiesConfiguration configuration = new CookiesConfiguration(null);

        assertThat(configuration.isSecure(request(true))).isTrue();
        assertThat(configuration.isSecure(request(false))).isFalse();
    }

    @Test
    void shouldOverrideRequestWhenSecureIsSet() {
        assertThat(new CookiesConfiguration(true).isSecure(request(false))).isTrue();
        assertThat(new CookiesConfiguration(false).isSecure(request(true))).isFalse();
    }

    private static HttpRequest<?> request(boolean secure) {
        HttpRequest<?> request = mock(HttpRequest.class);
        when(request.isSecure()).thenReturn(secure);
        when(request.getHeaders()).thenReturn(mock(HttpHeaders.class));
        return request;
    }
}
