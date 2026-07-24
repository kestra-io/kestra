package io.kestra.webserver.filter;

import org.junit.jupiter.api.Test;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end wiring checks: {@link EditionHeaderFilter} sets {@code X-Kestra-Edition} only on 404 responses. */
@MicronautTest(rebuildContext = true)
class EditionHeaderFilterIntegrationTest {
    @Inject
    @io.micronaut.http.client.annotation.Client("/")
    ReactorHttpClient client;

    @Inject
    ApplicationContext applicationContext;

    @Test
    void shouldSetEditionHeaderOnAnUnmatchedRoute404() {
        // Given
        assertThat(applicationContext.containsBean(EditionHeaderFilter.class)).isTrue();

        // When / Then
        assertThatThrownBy(() -> client.toBlocking().exchange(GET("/api/v1/main/this-route-does-not-exist")))
            .isInstanceOfSatisfying(HttpClientResponseException.class, e ->
                assertThat(e.getResponse().getHeaders().get(EditionHeaderFilter.EDITION_HEADER)).isEqualTo("OSS")
            );
    }

    @Test
    void shouldNotSetEditionHeaderOnASuccessfulResponse() {
        // When
        var response = client.toBlocking().exchange(GET("/ping"));

        // Then
        assertThat(response.getHeaders().contains(EditionHeaderFilter.EDITION_HEADER)).isFalse();
    }
}
