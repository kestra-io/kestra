package io.kestra.plugin.core.http;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.hc.core5.http.HttpEntity;

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
    @Schema(title = "The request URI.", description = "User-info is removed and query values holding credentials are replaced with `***`.") URI uri,
    @Schema(title = "The host of the request URI.") String host,
    @Schema(title = "The path of the request URI.") String path,
    @Schema(title = "The names of the query parameters.") List<String> queryParamNames,
    @Schema(title = "The names of the request headers explicitly set, values are never exposed.") List<String> headerNames,
    @Schema(title = "The content type of the request body.") String contentType,
    @Schema(title = "The size of the request body in bytes.") long bodyBytes,
    @Schema(title = "Whether the request has a body.") boolean hasBody,
    @Schema(title = "The names of the form fields, when the body is form data.") List<String> formFieldNames,
    @Schema(title = "The authentication mechanism, one of `NONE`, `BASIC`, `BEARER`, `DIGEST`, `API_KEY`, `AWS_SIGV4` or `AUTHORIZATION_HEADER`.") String auth,
    @Schema(title = "The read idle timeout, if configured.") Duration timeout,
    @Schema(title = "Whether redirects are followed.") Boolean followRedirects) {
    static RequestSummary of(HttpRequest request, HttpConfiguration options, Duration timeout, Boolean followRedirects, Set<String> secretValues) throws IOException {
        URI uri = request.getUri();

        List<String> headerNames = request.getHeaders() == null ? List.of() : new ArrayList<>(request.getHeaders().map().keySet());
        Set<String> lowerHeaderNames = new LinkedHashSet<>();
        headerNames.forEach(name -> lowerHeaderNames.add(name.toLowerCase(Locale.ROOT)));

        HttpRequest.RequestBody body = request.getBody();
        String contentType = body != null ? body.getContentType()
            : request.getHeaders() == null ? null : request.getHeaders().firstValue("Content-Type").orElse(null);

        List<String> formFieldNames = body != null && body.getContent() instanceof java.util.Map<?, ?> map
            ? map.keySet().stream().map(String::valueOf).toList()
            : List.of();

        return new RequestSummary(
            request.getMethod(),
            HttpRedactor.redactUri(uri, secretValues),
            uri.getHost(),
            uri.getPath(),
            queryParamNames(uri),
            headerNames,
            contentType,
            body == null ? 0 : bodyBytes(body),
            body != null,
            formFieldNames,
            auth(options, lowerHeaderNames),
            timeout,
            followRedirects
        );
    }

    private static List<String> queryParamNames(URI uri) {
        if (uri.getRawQuery() == null) {
            return List.of();
        }

        Set<String> names = new LinkedHashSet<>();
        for (String param : uri.getRawQuery().split("&")) {
            int eq = param.indexOf('=');
            names.add(URLDecoder.decode(eq < 0 ? param : param.substring(0, eq), StandardCharsets.UTF_8));
        }
        return List.copyOf(names);
    }

    private static long bodyBytes(HttpRequest.RequestBody body) throws IOException {
        HttpEntity entity = body.to();
        if (entity.getContentLength() >= 0) {
            return entity.getContentLength();
        }

        try (CountingOutputStream counter = new CountingOutputStream(NullOutputStream.NULL_OUTPUT_STREAM)) {
            entity.writeTo(counter);
            return counter.getByteCount();
        }
    }

    private static String auth(HttpConfiguration options, Set<String> lowerHeaderNames) {
        if (options != null && options.getAuth() != null) {
            return options.getAuth().getType().name();
        }
        if (lowerHeaderNames.contains("x-amz-date") || lowerHeaderNames.contains("x-amz-signature")) {
            return "AWS_SIGV4";
        }
        if (lowerHeaderNames.stream().anyMatch(name -> !"authorization".equals(name) && !"proxy-authorization".equals(name) && !"cookie".equals(name) && HttpRedactor.isSensitiveName(name))) {
            return "API_KEY";
        }
        return lowerHeaderNames.contains("authorization") ? "AUTHORIZATION_HEADER" : "NONE";
    }
}
