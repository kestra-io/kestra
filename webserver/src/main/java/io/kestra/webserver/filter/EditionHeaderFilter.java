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
 * Stamps an {@code X-Kestra-Edition} header on every 404 response. Runs at {@link ServerFilterPhase#FIRST},
 * like {@link SecurityHeadersFilter}, so it still applies to short-circuited or bare 404 responses.
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
