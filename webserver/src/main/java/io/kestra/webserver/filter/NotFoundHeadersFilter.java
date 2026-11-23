package io.kestra.webserver.filter;

import java.util.Objects;

import io.kestra.core.utils.EditionProvider;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
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
 * doesn't exist on this server). It is derived from the router's own match result, so no 404-producing call
 * site has to opt into tagging itself.
 * <p>
 * The match result has to be captured on the way <em>in</em>, not read on the way out: when an
 * {@code @Error} handler such as {@link io.kestra.webserver.controllers.ErrorController} takes over,
 * Micronaut overwrites the {@code ROUTE_MATCH} request attribute with the error route
 * ({@code RequestLifecycle#handleErrorRoute} calls {@code RouteExecutor#setRouteAttributes}), and an
 * {@code ErrorRouteMatch} is not a {@link io.micronaut.http.uri.UriMatchInfo} — so by the time response
 * filters run, {@link BasicHttpAttributes#getRouteMatchInfo} is empty even though a route did match. The
 * request filter below therefore records the verdict while it is still truthful; the response filter reads
 * that, and only falls back to the live attribute when the request filter never ran (an unmatched route
 * short-circuits before route filters, where an empty attribute is the correct answer anyway).
 * <p>
 * Note this makes edition and route existence readable by unauthenticated callers. Both are already public:
 * the edition via {@code /api/v1/configs}, and route existence via the published OpenAPI spec.
 */
@ServerFilter("/**")
public class NotFoundHeadersFilter implements Ordered {
    public static final String EDITION_HEADER = "X-Kestra-Edition";
    public static final String ROUTE_MATCHED_HEADER = "X-Kestra-Route-Matched";

    /** Internal request attribute holding the router's verdict, captured before any error route replaces it. */
    static final String ROUTE_MATCHED_ATTRIBUTE = "io.kestra.routeMatched";

    private final EditionProvider editionProvider;

    public NotFoundHeadersFilter(EditionProvider editionProvider) {
        this.editionProvider = Objects.requireNonNull(editionProvider);
    }

    /**
     * Records whether the router matched a route for this request. This filter is not pre-matching, so it runs
     * after the router has matched and before any {@code @Error} handler can replace the match attribute.
     *
     * @param request the incoming request
     */
    @RequestFilter
    public void rememberRouteMatch(@NonNull HttpRequest<?> request) {
        request.setAttribute(ROUTE_MATCHED_ATTRIBUTE, BasicHttpAttributes.getRouteMatchInfo(request).isPresent());
    }

    /**
     * Adds the 404-disambiguation headers, leaving any header a controller or another filter already set.
     *
     * @param request  the request that produced this response
     * @param response the outgoing response
     */
    @ResponseFilter
    public void addNotFoundHeaders(@NonNull HttpRequest<?> request, @NonNull MutableHttpResponse<?> response) {
        if (HttpStatus.NOT_FOUND != response.getStatus()) {
            return;
        }

        setIfAbsent(response, EDITION_HEADER, editionProvider.get().name());
        setIfAbsent(response, ROUTE_MATCHED_HEADER, String.valueOf(hasMatchedRoute(request)));
    }

    private static boolean hasMatchedRoute(HttpRequest<?> request) {
        return request.getAttribute(ROUTE_MATCHED_ATTRIBUTE, Boolean.class)
            .orElseGet(() -> BasicHttpAttributes.getRouteMatchInfo(request).isPresent());
    }

    /** Leaves an already-set header untouched, so a controller or another filter can still override it. */
    private static void setIfAbsent(MutableHttpResponse<?> response, String name, String value) {
        if (!response.getHeaders().contains(name)) {
            response.getHeaders().set(name, value);
        }
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.FIRST.order();
    }
}
