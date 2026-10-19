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

    static final String HEADERS_DESCRIPTION = "A header value is replaced with `***` when the header name is on the deny-list (`Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`, `X-Api-Key`, `X-Auth-Token`, `X-Amz-Security-Token`, `X-Amz-Signature`, `Ocp-Apim-Subscription-Key`), when the name contains `token`, `secret`, `password`, `credential`, `signature` or `api-key` (any case), when a whole word of the name is `key`, `sig`, `auth`, `pass` or `pwd` (so `page`, `monkey` and `keyword` are kept, and `Idempotency-Key` and `Sec-WebSocket-Key` are never redacted by name), or when the value is exactly a `secret()` resolved in the task. The `Location`, `Content-Location` and `Link` values are redacted as URIs: user-info is removed and only credential query or fragment parameters (`access_token`, `id_token`, `refresh_token`, `token`, `api_key`, `apikey`, `key`, `sig`, `signature`, `password`, `secret`, `auth`, `client_secret`, `code`, `X-Amz-Signature`, `X-Amz-Credential`, `X-Amz-Security-Token`) are masked, so pagination cursors such as `page_token` are kept. The `Access-Control-Allow-Credentials`, `Access-Control-Allow-Headers`, `Access-Control-Expose-Headers` and `Access-Control-Request-Headers` headers are never redacted by name.";
    static final String FINAL_URI_DESCRIPTION = "The URI reached after following redirects. The user-info is removed. Query and fragment values are replaced with `***` when the parameter name is sensitive (same name rules as `headers`, including the whole words `key`, `sig`, `auth`, `pass` and `pwd`) or the value is a resolved `secret()`. Path segments, or a run of consecutive segments, that are a resolved `secret()` are replaced with `***`, and a URI that is entirely a secret keeps only its scheme and host. Within the `Location` redirects, only credential parameters are masked, as described in `headers`.";
    static final String REDIRECTS_DESCRIPTION = "Each entry has the `code` of the redirect response and the `location` it pointed to, redacted like `finalUri`, with the same narrower credential-parameter rule as the `Location` header so pagination cursors are kept. Empty when no redirect was followed.";

    static ResponseSummary of(HttpResponse<?> response, URI requestUri, long contentLength, long durationMs, Set<String> secretValues) {
        List<HttpResponse.Redirect> redirects = response.getRedirects() == null ? List.of()
            : response.getRedirects()
                .stream()
                .map(redirect -> HttpResponse.Redirect.builder().code(redirect.getCode()).location(HttpRedactor.redactRedirectLocation(redirect.getLocation(), secretValues)).build())
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
