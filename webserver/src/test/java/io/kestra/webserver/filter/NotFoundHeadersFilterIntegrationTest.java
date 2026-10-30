package io.kestra.webserver.filter;

import org.junit.jupiter.api.Test;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end wiring checks: {@link NotFoundHeadersFilter} sets {@code X-Kestra-Edition} and
 * {@code X-Kestra-Route-Matched} only on 404 responses. Header-value logic itself is covered by the plain unit
 * tests in {@link NotFoundHeadersFilterTest}.
 * <p>
 * The two matched-route cases below cover both ways Kestra produces a 404 from a route that exists: a status
 * route (the controller returns {@code null}) and an {@code @Error}-handled exception. They differ in the
 * router internals — an error route replaces the {@code ROUTE_MATCH} attribute, a status route does not — so
 * both have to be exercised.
 */
@MicronautTest
class NotFoundHeadersFilterIntegrationTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    ApplicationContext applicationContext;

    @Test
    void shouldSetEditionHeaderOnAnUnmatchedRoute404() {
        // Given
        assertThat(applicationContext.containsBean(NotFoundHeadersFilter.class)).isTrue();

        // When / Then
        assertThatThrownBy(() -> client.toBlocking().exchange(GET("/api/v1/main/this-route-does-not-exist")))
            .isInstanceOfSatisfying(HttpClientResponseException.class, e ->
                assertThat(e.getResponse().getHeaders().get(NotFoundHeadersFilter.EDITION_HEADER)).isEqualTo("OSS")
            );
    }

    @Test
    void shouldSetRouteMatchedHeaderToFalseOnAnUnmatchedRoute404() {
        // When / Then
        assertThatThrownBy(() -> client.toBlocking().exchange(GET("/api/v1/main/this-route-does-not-exist")))
            .isInstanceOfSatisfying(HttpClientResponseException.class, e ->
                assertThat(e.getResponse().getHeaders().get(NotFoundHeadersFilter.ROUTE_MATCHED_HEADER)).isEqualTo("false")
            );
    }

    @Test
    void shouldSetRouteMatchedHeaderToTrueOnANotFoundFromAStatusRoute() {
        // Given - GET /flows/{namespace}/{id} returns null for an unknown flow, which Micronaut turns into a
        // 404 status route; the original route match stays on the request

        // When / Then
        assertThatThrownBy(() -> client.toBlocking().exchange(GET("/api/v1/main/flows/io.kestra.tests/notFound")))
            .isInstanceOfSatisfying(HttpClientResponseException.class, e -> {
                assertThat(e.getResponse().getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
                assertThat(e.getResponse().getHeaders().get(NotFoundHeadersFilter.ROUTE_MATCHED_HEADER)).isEqualTo("true");
            });
    }

    @Test
    void shouldSetRouteMatchedHeaderToTrueOnANotFoundExceptionFromAMatchedRoute() {
        // Given - GET /plugins/{group}/pluginUi/{path} throws NotFoundException for an unknown plugin group,
        // so ErrorController handles it and Micronaut replaces the route match with an ErrorRouteMatch

        // When / Then
        assertThatThrownBy(() -> client.toBlocking().exchange(GET("/api/v1/plugins/io.kestra.plugin.does.not.exist/pluginUi/index.js")))
            .isInstanceOfSatisfying(HttpClientResponseException.class, e -> {
                assertThat(e.getResponse().getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
                assertThat(e.getResponse().getHeaders().get(NotFoundHeadersFilter.ROUTE_MATCHED_HEADER)).isEqualTo("true");
            });
    }

    @Test
    void shouldNotSetEditionHeaderOnASuccessfulResponse() {
        // When
        var response = client.toBlocking().exchange(GET("/ping"));

        // Then
        assertThat(response.getHeaders().contains(NotFoundHeadersFilter.EDITION_HEADER)).isFalse();
        assertThat(response.getHeaders().contains(NotFoundHeadersFilter.ROUTE_MATCHED_HEADER)).isFalse();
    }
}
