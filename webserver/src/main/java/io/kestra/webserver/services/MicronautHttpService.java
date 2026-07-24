package io.kestra.webserver.services;

import java.io.InputStream;
import java.net.http.HttpHeaders;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;

public abstract class MicronautHttpService {

    public static HttpRequest from(io.micronaut.http.HttpRequest<?> request) {
        return from(request, requestBody(request));
    }

    /**
     * Convert a Micronaut request, using the given body instead of the one carried by the request.
     * Used when the body has already been consumed by the route, e.g. a {@code multipart/form-data} body
     * bound as parts.
     *
     * @param request the Micronaut request to convert
     * @param body    the body to attach to the converted request, may be {@code null}
     * @return the converted request
     */
    public static HttpRequest from(io.micronaut.http.HttpRequest<?> request, HttpRequest.RequestBody body) {
        return HttpRequest.builder()
            .uri(request.getUri())
            .method(request.getMethod().name())
            .body(body)
            .headers(HttpHeaders.of(request.getHeaders().asMap(), (a, b) -> true))
            .remoteAddress(request.getRemoteAddress())
            .build();
    }

    private static HttpRequest.RequestBody requestBody(io.micronaut.http.HttpRequest<?> request) {
        if (request.getBody().isEmpty()) {
            return null;
        }

        Object bodyContent = request.getBody().get();

        if (bodyContent instanceof InputStream inputStream) {
            return HttpRequest.InputStreamRequestBody.builder()
                .content(inputStream)
                .build();
        } else if (bodyContent instanceof byte[] bytes) {
            return byteArrayRequestBody(request, bytes);
        } else if (bodyContent instanceof String str) {
            return HttpRequest.StringRequestBody.builder()
                .content(str)
                .build();
        } else {
            return HttpRequest.JsonRequestBody.builder()
                .content(bodyContent)
                .build();
        }
    }

    /**
     * Build the body of a request received as raw bytes: text is decoded to a string, anything else is kept as
     * bytes so that binary content survives.
     */
    private static HttpRequest.RequestBody byteArrayRequestBody(io.micronaut.http.HttpRequest<?> request, byte[] bytes) {
        if (bytes.length == 0) {
            // A request without a body is bound as an empty array: report it as no body at all.
            return null;
        }

        MediaType contentType = request.getContentType().orElse(null);
        Charset charset = contentType != null ? contentType.getCharset().orElse(StandardCharsets.UTF_8) : StandardCharsets.UTF_8;

        if (isTextual(contentType)) {
            HttpRequest.StringRequestBody.StringRequestBodyBuilder<?, ?> builder = HttpRequest.StringRequestBody.builder()
                .charset(charset)
                .content(new String(bytes, charset));

            return (contentType != null ? builder.contentType(contentType.getName()) : builder).build();
        }

        return HttpRequest.ByteArrayRequestBody.builder()
            .contentType(contentType.getName())
            .charset(charset)
            .content(bytes)
            .build();
    }

    /**
     * Whether a body of the given content type can be decoded to a string. A request without a content type is
     * assumed to be textual, as it was before binary bodies were supported.
     * <p>
     * {@link MediaType#isTextBased()} covers {@code text/*}, JSON and XML (including the {@code +json} and
     * {@code +xml} suffixes); the types added here are textual payloads it does not know about, and which
     * webhook callers do send.
     */
    private static boolean isTextual(MediaType contentType) {
        if (contentType == null || contentType.isTextBased()) {
            return true;
        }

        return MediaType.APPLICATION_FORM_URLENCODED_TYPE.getName().equalsIgnoreCase(contentType.getName())
            || "yaml".equalsIgnoreCase(contentType.getSubtype())
            || "csv".equalsIgnoreCase(contentType.getSubtype());
    }

    public static <T> io.micronaut.http.HttpResponse<?> to(HttpResponse<T> response) {
        var result = io.micronaut.http.HttpResponse
            .status(HttpStatus.valueOf(response.getStatus().getCode()))
            .headers(headers ->
            {
                if (response.getHeaders() != null) {
                    response.getHeaders().map().forEach((key, values) ->
                    {
                        for (String value : values) {
                            headers.add(key, value);
                        }
                    });
                }
            });

        if (response.getBody() instanceof byte[] bytes) {
            return result.body(bytes);
        } else if (response.getBody() instanceof String str) {
            return result.body(str);
        } else if (response.getBody() instanceof InputStream inputStream) {
            return result.body(inputStream);
        } else if (response.getBody() != null) {
            return result.body(response.getBody());
        } else {
            return result;
        }
    }
}