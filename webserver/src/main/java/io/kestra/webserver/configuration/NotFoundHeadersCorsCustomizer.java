package io.kestra.webserver.configuration;

import java.util.ArrayList;
import java.util.List;

import io.kestra.webserver.filter.NotFoundHeadersFilter;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.cors.CorsOriginConfiguration;
import jakarta.inject.Singleton;

/**
 * Adds Kestra's 404-disambiguation headers to the {@code exposed-headers} of every CORS configuration a
 * deployment defines, so browser clients on another origin can actually read them — a browser hides any
 * response header that is not listed in {@code Access-Control-Expose-Headers}, and the SDK then silently
 * falls back to its old guess-from-the-route heuristic.
 * <p>
 * This only appends two header names to configurations that already exist. It never creates a configuration
 * and never touches origins, credentials or methods: shipping a named CORS configuration as an OSS default
 * would be unsafe, since one declared without {@code allowedOrigins} matches <em>any</em> origin with
 * credentials allowed, and {@code CorsFilter} uses the first configuration matching the request origin — so it
 * could silently shadow a deployment's own origin-restricted configuration.
 */
@Singleton
public class NotFoundHeadersCorsCustomizer implements BeanCreatedEventListener<HttpServerConfiguration.CorsConfiguration> {
    private static final List<String> NOT_FOUND_HEADERS = List.of(
        NotFoundHeadersFilter.EDITION_HEADER,
        NotFoundHeadersFilter.ROUTE_MATCHED_HEADER
    );

    @Override
    public HttpServerConfiguration.CorsConfiguration onCreated(BeanCreatedEvent<HttpServerConfiguration.CorsConfiguration> event) {
        HttpServerConfiguration.CorsConfiguration corsConfiguration = event.getBean();
        if (!corsConfiguration.isEnabled()) {
            return corsConfiguration;
        }

        corsConfiguration.getConfigurations().values().forEach(NotFoundHeadersCorsCustomizer::expose);

        return corsConfiguration;
    }

    private static void expose(CorsOriginConfiguration originConfiguration) {
        List<String> exposedHeaders = new ArrayList<>(originConfiguration.getExposedHeaders());
        NOT_FOUND_HEADERS.stream()
            .filter(header -> exposedHeaders.stream().noneMatch(header::equalsIgnoreCase))
            .forEach(exposedHeaders::add);

        originConfiguration.setExposedHeaders(exposedHeaders);
    }
}
