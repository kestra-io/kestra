package io.kestra.webserver.filter;

import org.junit.jupiter.api.Test;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class PluginEndpointBodySizeFilterTest {
    @Inject
    @Client("/")
    private ReactorHttpClient client;

    @Test
    void shouldRejectPostWithContentLengthOverCap() {
        // Given - a body above MAX_BODY_SIZE on the plugin-endpoints path
        byte[] oversizedBody = new byte[(int) PluginEndpointBodySizeFilter.MAX_BODY_SIZE + 1];
        MutableHttpRequest<?> request = HttpRequest.POST("/api/v1/main/plugins/io.kestra.plugin.core/endpoints/foo", oversizedBody);

        // When/Then
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(request)
        );
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.REQUEST_ENTITY_TOO_LARGE.getCode());
    }

    @Test
    void shouldNotBlockSmallRequest() {
        // Given - a small request on the same path; downstream 404 is fine, 413 is not
        MutableHttpRequest<?> request = HttpRequest.POST("/api/v1/main/plugins/io.kestra.plugin.core/endpoints/foo", "small body");

        // When/Then
        HttpClientResponseException exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(request)
        );
        assertThat(exception.getStatus().getCode()).isNotEqualTo(HttpStatus.REQUEST_ENTITY_TOO_LARGE.getCode());
    }
}
