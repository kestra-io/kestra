package io.kestra.webserver.filter;

import io.kestra.webserver.controllers.domain.MarketplaceRequestType;
import io.kestra.webserver.services.MarketplaceRequestMapper;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.filter.*;
import io.micronaut.web.router.RouteMatch;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Filter("/api/v1/**/editor/marketplace/**")
public class MarketplaceFilter implements HttpServerFilter {
    @Inject
    @Client("proxy")
    private ProxyHttpClient httpClient;

    @Inject
    private MarketplaceRequestMapper marketplaceRequestMapper;

    @Override
    public int getOrder() {
        return ServerFilterPhase.RENDERING.order();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        MutableHttpRequest<?> httpRequest = request.mutate();

        httpRequest.headers(headers -> {
            if (Optional.ofNullable(headers.get("Origin")).map(origin -> !origin.contains("localhost")).orElse(true)) {
                headers.set("Origin", "http://localhost:8080");
            }
            headers.remove("Cookie");
            headers.remove("Accept-Encoding");
        });

        Map<String, Object> matchValues = (Map<String, Object>) request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class)
            .map(RouteMatch::getVariableValues)
            .orElse(Collections.emptyMap());

        MarketplaceRequestType type = Optional.ofNullable(matchValues.get("type"))
            .map(String.class::cast)
            .map(MarketplaceRequestType::fromString)
            .orElse(null);

        String path = Optional.ofNullable(matchValues.get("path")).map(Object::toString).orElse("");

        Publisher<MutableHttpResponse<?>> publisher;
        if (type == null) {
            publisher = chain.proceed(httpRequest);
        } else {
            httpRequest.uri(URI.create(marketplaceRequestMapper.url(type) + path));

            publisher = httpClient.proxy(httpRequest);
        }

        return Publishers.map(
            publisher,
            mutableHttpResponse -> mutableHttpResponse.headers(headers -> headers.remove("Access-Control-Allow-Origin"))
        );
    }
}
