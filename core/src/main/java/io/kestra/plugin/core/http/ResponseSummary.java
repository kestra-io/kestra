package io.kestra.plugin.core.http;

import java.net.URI;
import java.util.List;
import java.util.Set;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpRedactor;

record ResponseSummary(
    String reason,
    String contentType,
    long contentLength,
    long durationMs,
    String finalUri,
    List<HttpResponse.Redirect> redirects,
    String protocol,
    int retries) {
    static ResponseSummary of(HttpResponse<?> response, URI requestUri, long contentLength, long durationMs, Set<String> secretValues) {
        List<HttpResponse.Redirect> redirects = response.getRedirects() == null ? List.of()
            : response.getRedirects()
                .stream()
                .map(redirect -> HttpResponse.Redirect.builder().code(redirect.getCode()).location(HttpRedactor.redactUri(redirect.getLocation(), secretValues)).build())
                .toList();

        String finalUri = redirects.isEmpty() ? HttpRedactor.redactUri(requestUri, secretValues).toString() : redirects.getLast().getLocation();

        return new ResponseSummary(
            response.getStatus().getReason(),
            response.getHeaders() == null ? null : response.getHeaders().firstValue("Content-Type").orElse(null),
            contentLength,
            durationMs,
            finalUri,
            redirects,
            response.getProtocol(),
            response.getRetries() == null ? 0 : response.getRetries()
        );
    }
}
