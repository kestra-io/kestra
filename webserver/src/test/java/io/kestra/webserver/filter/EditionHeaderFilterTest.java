package io.kestra.webserver.filter;

import org.junit.jupiter.api.Test;

import io.kestra.core.utils.EditionProvider;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.UriRouteMatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Header-value logic for {@link EditionHeaderFilter}. Wiring into a real HTTP response is covered by
 * {@link EditionHeaderFilterIntegrationTest}.
 */
class EditionHeaderFilterTest {
    private static final EditionHeaderFilter FILTER = new EditionHeaderFilter(new EditionProvider());

    @Test
    void shouldSetBothHeadersOnA404FromAnUnmatchedRoute() {
        // Given
        MutableHttpRequest<?> request = unmatchedRequest();
        MutableHttpResponse<?> response = HttpResponse.notFound();

        // When
        FILTER.addEditionHeader(request, response);

        // Then
        assertThat(response.getHeaders().get(EditionHeaderFilter.EDITION_HEADER)).isEqualTo("OSS");
        assertThat(response.getHeaders().get(EditionHeaderFilter.ROUTE_MATCHED_HEADER)).isEqualTo("false");
    }

    @Test
    void shouldSetRouteMatchedToTrueWhenTheRequestMatchedARoute() {
        // Given
        MutableHttpRequest<?> request = matchedRequest();
        MutableHttpResponse<?> response = HttpResponse.notFound();

        // When
        FILTER.addEditionHeader(request, response);

        // Then
        assertThat(response.getHeaders().get(EditionHeaderFilter.ROUTE_MATCHED_HEADER)).isEqualTo("true");
    }

    @Test
    void shouldSetTheEditionReportedByTheEditionProvider() {
        // Given
        EditionProvider editionProvider = mock(EditionProvider.class);
        when(editionProvider.get()).thenReturn(EditionProvider.Edition.EE);
        MutableHttpResponse<?> response = HttpResponse.notFound();

        // When
        new EditionHeaderFilter(editionProvider).addEditionHeader(unmatchedRequest(), response);

        // Then
        assertThat(response.getHeaders().get(EditionHeaderFilter.EDITION_HEADER)).isEqualTo("EE");
    }

    @Test
    void shouldNotOverrideAlreadySetHeaders() {
        // Given - a controller or another filter already set both headers
        MutableHttpRequest<?> request = unmatchedRequest();
        MutableHttpResponse<?> response = HttpResponse.notFound();
        response.getHeaders().set(EditionHeaderFilter.EDITION_HEADER, "EE");
        response.getHeaders().set(EditionHeaderFilter.ROUTE_MATCHED_HEADER, "true");

        // When
        FILTER.addEditionHeader(request, response);

        // Then
        assertThat(response.getHeaders().get(EditionHeaderFilter.EDITION_HEADER)).isEqualTo("EE");
        assertThat(response.getHeaders().get(EditionHeaderFilter.ROUTE_MATCHED_HEADER)).isEqualTo("true");
    }

    @Test
    void shouldNotSetAnyHeaderOnANon404Response() {
        // Given
        MutableHttpRequest<?> request = matchedRequest();
        MutableHttpResponse<?> response = HttpResponse.ok();

        // When
        FILTER.addEditionHeader(request, response);

        // Then
        assertThat(response.getHeaders().contains(EditionHeaderFilter.EDITION_HEADER)).isFalse();
        assertThat(response.getHeaders().contains(EditionHeaderFilter.ROUTE_MATCHED_HEADER)).isFalse();
    }

    private static MutableHttpRequest<?> unmatchedRequest() {
        return HttpRequest.GET("/api/v1/main/this-route-does-not-exist");
    }

    /** A request carrying the router's own match result, as the server sets it once a route matched. */
    private static MutableHttpRequest<?> matchedRequest() {
        MutableHttpRequest<?> request = HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/notFound");
        RouteAttributes.setRouteMatch(request, mock(UriRouteMatch.class));

        return request;
    }
}
