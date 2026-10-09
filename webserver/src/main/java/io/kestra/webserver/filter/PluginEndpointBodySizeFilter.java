package io.kestra.webserver.filter;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;

@ServerFilter("/api/v1/*/plugins/*/endpoints/**")
public class PluginEndpointBodySizeFilter {
    static final long DEFAULT_MAX_BODY_SIZE = 10 * 1024 * 1024;

    private final long maxBodySize;

    public PluginEndpointBodySizeFilter(
        @Value("${kestra.plugins.endpoint.maxBodySize:10485760}") long maxBodySize
    ) {
        this.maxBodySize = maxBodySize;
    }

    @RequestFilter
    @Nullable
    public HttpResponse<?> filterRequest(@NonNull HttpRequest<?> request) {
        // getContentLength() is -1 when unknown (e.g. chunked); those still ride the global max-request-size cap.
        if (request.getContentLength() > maxBodySize) {
            return HttpResponse.status(HttpStatus.REQUEST_ENTITY_TOO_LARGE);
        }

        return null;
    }
}
