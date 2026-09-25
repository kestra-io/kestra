package io.kestra.webserver.filter;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;

@ServerFilter("/api/v1/*/plugins/*/endpoints/*")
public class PluginEndpointBodySizeFilter {
    static final long MAX_BODY_SIZE = 10 * 1024 * 1024;

    @RequestFilter
    @Nullable
    public HttpResponse<?> filterRequest(@NonNull HttpRequest<?> request) {
        // getContentLength() is -1 when unknown (e.g. chunked); those still ride the global max-request-size cap.
        if (request.getContentLength() > MAX_BODY_SIZE) {
            return HttpResponse.status(HttpStatus.REQUEST_ENTITY_TOO_LARGE);
        }

        return null;
    }
}
