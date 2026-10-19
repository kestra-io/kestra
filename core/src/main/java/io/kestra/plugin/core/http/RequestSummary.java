package io.kestra.plugin.core.http;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpRedactor;
import io.kestra.core.http.client.configurations.HttpConfiguration;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    title = "A summary of the request that was sent.",
    description = "Built after rendering and before sending. It never contains header values, query values or body content, only their names and sizes."
)
public record RequestSummary(
    @Schema(title = "The HTTP method.") String method,
    @Schema(
        title = "The request URI.",
        description = "User-info is removed. Query and fragment values holding credentials, and path segments (or runs of segments) that are a resolved `secret()`, are replaced with `***`."
    ) URI uri,
    @Schema(title = "The host of the request URI.") String host,
    @Schema(title = "The path of the request URI.") String path,
    @Schema(title = "The names of the query parameters.") List<String> queryParamNames,
    @Schema(title = "The names of the request headers explicitly set, values are never exposed.") List<String> headerNames,
    @Schema(title = "The content type of the request body.") String contentType,
    @Schema(title = "The size of the request body in bytes.", description = "`-1` when the length is not known before sending.") long bodyBytes,
    @Schema(title = "Whether the request has a body.") boolean hasBody,
    @Schema(title = "The names of the form fields, when the body is form data.") List<String> formFieldNames,
    @Schema(title = "The authentication mechanism, one of `NONE`, `BASIC`, `BEARER`, `DIGEST`, `API_KEY`, `AWS_SIGV4` or `AUTHORIZATION_HEADER`.") AuthType auth,
    @Schema(title = "The read idle timeout, if configured.") Duration timeout,
    @Schema(title = "Whether redirects are followed.") Boolean followRedirects) {
    static RequestSummary of(HttpRequest request, HttpConfiguration options, Duration timeout, Boolean followRedirects, Set<String> secretValues) throws IOException {
        URI uri = request.getUri();
        URI redactedUri = HttpRedactor.redactUri(uri, secretValues);

        List<String> headerNames = request.getHeaders() == null ? List.of() : new ArrayList<>(request.getHeaders().map().keySet());
        Set<String> lowerHeaderNames = new LinkedHashSet<>();
        headerNames.forEach(name -> lowerHeaderNames.add(name.toLowerCase(Locale.ROOT)));

        HttpRequest.RequestBody body = request.getBody();
        String contentType = body != null ? body.getContentType()
            : request.getHeaders() == null ? null : request.getHeaders().firstValue("Content-Type").orElse(null);

        List<String> formFieldNames = body != null && body.getContent() instanceof Map<?, ?> map
            ? map.keySet().stream().map(String::valueOf).toList()
            : List.of();

        return new RequestSummary(
            request.getMethod(),
            redactedUri,
            uri.getHost(),
            redactedUri.getPath(),
            queryParamNames(uri, secretValues),
            headerNames,
            contentType,
            body == null ? 0 : bodyBytes(body),
            body != null,
            formFieldNames,
            auth(options, request, lowerHeaderNames),
            timeout,
            followRedirects
        );
    }

    private static List<String> queryParamNames(URI uri, Set<String> secretValues) {
        Set<String> names = new LinkedHashSet<>();
        for (NameValuePair param : new URIBuilder(uri).getQueryParams()) {
            names.add(HttpRedactor.isSecretValue(param.getName(), secretValues) ? "***" : param.getName());
        }
        return List.copyOf(names);
    }

    private static long bodyBytes(HttpRequest.RequestBody body) throws IOException {
        // Counting an unknown length would consume a non-repeatable entity before it is sent.
        return body.to().getContentLength();
    }

    private static AuthType auth(HttpConfiguration options, HttpRequest request, Set<String> lowerHeaderNames) {
        if (options != null && options.getAuth() != null) {
            return switch (options.getAuth().getType()) {
                case BASIC -> AuthType.BASIC;
                case BEARER -> AuthType.BEARER;
                case DIGEST -> AuthType.DIGEST;
            };
        }
        if (lowerHeaderNames.contains("authorization")) {
            // Only the scheme prefix is read, the credential itself is never kept.
            String scheme = request.getHeaders().firstValue("Authorization").orElse("").trim().toLowerCase(Locale.ROOT);
            if (scheme.startsWith("bearer ")) {
                return AuthType.BEARER;
            }
            if (scheme.startsWith("basic ")) {
                return AuthType.BASIC;
            }
            if (scheme.startsWith("aws4-hmac-sha256")) {
                return AuthType.AWS_SIGV4;
            }
        }
        if (lowerHeaderNames.contains("x-amz-date") || lowerHeaderNames.contains("x-amz-signature")) {
            return AuthType.AWS_SIGV4;
        }
        if (lowerHeaderNames.stream().anyMatch(name -> !"authorization".equals(name) && !"proxy-authorization".equals(name) && !"cookie".equals(name) && HttpRedactor.isSensitiveName(name))) {
            return AuthType.API_KEY;
        }
        return lowerHeaderNames.contains("authorization") ? AuthType.AUTHORIZATION_HEADER : AuthType.NONE;
    }

    public enum AuthType {
        NONE,
        BASIC,
        BEARER,
        DIGEST,
        API_KEY,
        AWS_SIGV4,
        AUTHORIZATION_HEADER
    }
}
