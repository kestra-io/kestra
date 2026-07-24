package io.kestra.webserver.filter;

import java.util.Objects;

import io.kestra.core.utils.EditionProvider;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;

/**
 * Stamps an {@code X-Kestra-Edition} header on every 404 response, so a client can tell a 404 caused by an
 * unmatched or not-yet-shipped route on this server's edition apart from a 404 caused by a genuinely missing
 * resource, without an extra round trip to fetch {@code /api/v1/configs}.
 * <p>
 * Runs at {@link ServerFilterPhase#FIRST}, i.e. as the outermost filter, for the same reason as
 * {@link SecurityHeadersFilter}: it must still apply to 404 responses that short-circuit the filter chain or are
 * rebuilt wholesale by another filter. This covers every 404 code path uniformly, including the ones that never
 * go through {@link io.kestra.webserver.controllers.ErrorController} at all (e.g. a controller returning
 * {@code HttpResponse.notFound()} directly).
 */
@ServerFilter("/**")
public class EditionHeaderFilter implements Ordered {
    public static final String EDITION_HEADER = "X-Kestra-Edition";

    private final EditionProvider editionProvider;

    public EditionHeaderFilter(EditionProvider editionProvider) {
        this.editionProvider = Objects.requireNonNull(editionProvider);
    }

    @ResponseFilter
    public void addEditionHeader(@NonNull HttpRequest<?> request, @NonNull MutableHttpResponse<?> response) {
        if (response.getStatus() == HttpStatus.NOT_FOUND && !response.getHeaders().contains(EDITION_HEADER)) {
            response.getHeaders().set(EDITION_HEADER, editionProvider.get().name());
        }
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.FIRST.order();
    }
}
