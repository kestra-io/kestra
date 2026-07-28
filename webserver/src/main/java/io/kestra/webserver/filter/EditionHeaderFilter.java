package io.kestra.webserver.filter;

import java.util.Objects;

import io.kestra.core.utils.EditionProvider;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;

/**
 * Stamps {@code X-Kestra-Edition} and {@code X-Kestra-Route-Matched} headers on every 404 response.
 * Runs at {@link ServerFilterPhase#FIRST}, like {@link SecurityHeadersFilter}, so it still applies to
 * short-circuited or bare 404 responses.
 * <p>
 * {@code X-Kestra-Route-Matched} tells a client whether this 404 came from a route that exists on this
 * server (a genuine not-found from application code) or from no route matching at all (the route/feature
 * doesn't exist on this server). This is derived directly from the router's own match result, so it is
 * always accurate — unlike asking every 404-producing call site to opt into tagging itself.
 */
@ServerFilter("/**")
public class EditionHeaderFilter implements Ordered {
    public static final String EDITION_HEADER = "X-Kestra-Edition";
    public static final String ROUTE_MATCHED_HEADER = "X-Kestra-Route-Matched";

    private final EditionProvider editionProvider;

    public EditionHeaderFilter(EditionProvider editionProvider) {
        this.editionProvider = Objects.requireNonNull(editionProvider);
    }

    @ResponseFilter
    public void addEditionHeader(@NonNull HttpRequest<?> request, @NonNull MutableHttpResponse<?> response) {
        if (response.getStatus() == HttpStatus.NOT_FOUND) {
            if (!response.getHeaders().contains(EDITION_HEADER)) {
                response.getHeaders().set(EDITION_HEADER, editionProvider.get().name());
            }
            if (!response.getHeaders().contains(ROUTE_MATCHED_HEADER)) {
                boolean routeMatched = BasicHttpAttributes.getRouteMatchInfo(request).isPresent();
                response.getHeaders().set(ROUTE_MATCHED_HEADER, String.valueOf(routeMatched));
            }
        }
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.FIRST.order();
    }
}
