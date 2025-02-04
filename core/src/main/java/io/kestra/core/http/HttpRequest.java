package io.kestra.core.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.entity.mime.*;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Builder
@Value
public class HttpRequest {
    /**
     * The request method for this request. If not set explicitly,
     * the default method for any request is "GET".
     */
    @Builder.Default
    String method = "GET";

    /**
     * This request's {@code URI}.
     */
    URI uri;

    /**
     * This body {@code RequestBody}.
     */
    RequestBody body;

    /**
     * The (user-accessible) request headers that this request was (or will be) sent with.
     */
    HttpHeaders headers;

    public static HttpRequest from(org.apache.hc.core5.http.HttpRequest request) throws IOException {
        return HttpRequest.builder()
            .uri(HttpService.safeURI(request))
            .method(request.getMethod())
            .body(RequestBody.from(request instanceof ClassicHttpRequest classicHttpRequest ? classicHttpRequest.getEntity() : null))
            .headers(HttpService.toHttpHeaders(request.getHeaders()))
            .build();
    }

    public static HttpRequest of(URI uri) {
        return HttpRequest.builder()
            .uri(uri)
            .build();
    }

    public static HttpRequest of(URI uri, Map<String, List<String>> headers) {
        return HttpRequest.builder()
            .uri(uri)
            .headers(HttpHeaders.of(headers, (a, b) -> true))
            .build();
    }

    public static HttpRequest of(URI uri, String method, RequestBody body) {
        return HttpRequest.builder()
            .method(method)
            .uri(uri)
            .body(body)
            .build();
    }

    public static HttpRequest of(URI uri, String method, RequestBody body, Map<String, List<String>> headers) {
        return HttpRequest.builder()
            .method(method)
            .uri(uri)
            .body(body)
            .headers(HttpHeaders.of(headers, (a, b) -> true))
            .build();
    }

    public HttpUriRequest to(RunContext runContext) throws IOException {
        HttpUriRequestBase builder = new HttpUriRequestBase(this.method, this.uri);

        // headers
        if (this.headers != null) {
            this.headers.map()
                .forEach((key, value) -> value
                    .forEach(headerValue -> builder.addHeader(key, headerValue))
                );
        }

        if (runContext.getTraceParent() != null) {
            builder.addHeader("traceparent", runContext.getTraceParent());
        }

        // body
        if (this.body != null) {
            builder.setEntity(this.body.to());
        }

        return builder;
    }


    public static class HttpRequestBuilder  {
        public HttpRequestBuilder addHeader(String name, String value) {
            Map<String, List<String>> allHeaders = new HashMap<>(this.headers == null ? Map.of() : this.headers.map());

            if (allHeaders.containsKey(name)) {
                List<String> current = allHeaders.get(name);
                current.add(value);

                allHeaders.put(name, current);
            } else {
                allHeaders.put(name, List.of(value));
            }

            this.headers = HttpHeaders.of(allHeaders, (a, b) -> true);

            return this;
        }
    }

    @AllArgsConstructor
    @SuperBuilder
    public abstract static class RequestBody {
        public abstract HttpEntity to() throws IOException;

        public static RequestBody from(HttpEntity entity) throws IOException {
            if (entity == null) {
                return null;
            }

            Charset charset = Charset.forName(entity.getContentEncoding());

            if (entity.getContentType().equals(ContentType.APPLICATION_OCTET_STREAM.getMimeType())) {
                return ByteArrayRequestBody.builder()
                    .contentType(entity.getContentType())
                    .charset(charset)
                    .content(IOUtils.toByteArray(entity.getContent()))
                    .build();
            }

            if (entity.getContentType().equals(ContentType.TEXT_PLAIN.getMimeType())) {
                return StringRequestBody.builder()
                    .contentType(entity.getContentType())
                    .charset(charset)
                    .content(IOUtils.toString(entity.getContent(), charset))
                    .build();
            }

            if (entity.getContentType().equals(ContentType.APPLICATION_JSON.getMimeType())) {
                return JsonRequestBody.builder()
                    .charset(charset)
                    .content(JacksonMapper.toObject(IOUtils.toString(entity.getContent(), charset)))
                    .build();
            }

            throw new IllegalArgumentException("Unsupported Content-Type: " + entity.getContentType());
        }
    }

    @AllArgsConstructor
    @SuperBuilder
    public static class InputStreamRequestBody extends RequestBody {
        @Builder.Default
        private String contentType = ContentType.APPLICATION_OCTET_STREAM.getMimeType();

        @Builder.Default
        private Charset charset = StandardCharsets.UTF_8;

        private InputStream content;

        public HttpEntity to() {
            return new InputStreamEntity(content, ContentType.create(contentType, charset));
        }
    }

    @AllArgsConstructor
    @SuperBuilder
    public static class StringRequestBody extends RequestBody {
        @Builder.Default
        private String contentType = ContentType.TEXT_PLAIN.getMimeType();

        @Builder.Default
        private Charset charset = StandardCharsets.UTF_8;

        private String content;

        public HttpEntity to() {
            return new StringEntity(this.content, ContentType.create(contentType, charset));
        }
    }

    @AllArgsConstructor
    @SuperBuilder
    public static class ByteArrayRequestBody extends RequestBody {
        @Builder.Default
        private String contentType = ContentType.APPLICATION_OCTET_STREAM.getMimeType();

        @Builder.Default
        private Charset charset = StandardCharsets.UTF_8;

        private byte[] content;

        public HttpEntity to() {
            return new ByteArrayEntity(content, ContentType.create(contentType, charset));
        }
    }

    @AllArgsConstructor
    @SuperBuilder
    public static class JsonRequestBody extends RequestBody {
        @Builder.Default
        private Charset charset = StandardCharsets.UTF_8;

        private Object content;

        public HttpEntity to() throws IOException {
            try {
                return new StringEntity(
                    JacksonMapper.ofJson().writeValueAsString(content),
                    ContentType.APPLICATION_JSON.withCharset(this.charset)
                );
            } catch (JsonProcessingException e) {
                throw new IOException(e);
            }
        }
    }

    @AllArgsConstructor
    @SuperBuilder
    public static class UrlEncodedRequestBody extends RequestBody {
        @Builder.Default
        private Charset charset = StandardCharsets.UTF_8;

        private Map<String, Object> content;

        public HttpEntity to() throws IOException {
            return new UrlEncodedFormEntity(
                this.content .entrySet()
                    .stream()
                    .map(e -> new BasicNameValuePair(e.getKey(), e.getValue().toString()))
                    .toList(),
                this.charset
            );
        }
    }

    @AllArgsConstructor
    @SuperBuilder
    public static class MultipartRequestBody extends RequestBody {
        @Builder.Default
        private Charset charset = StandardCharsets.UTF_8;

        private Map<String, Object> content;

        public HttpEntity to() throws IOException {
            MultipartEntityBuilder builder = MultipartEntityBuilder
                .create()
                .setCharset(this.charset);

            content.forEach((key, value) -> {
                switch (value) {
                    case File fileValue -> builder.addPart(
                        key,
                        new FileBody(fileValue, ContentType.DEFAULT_BINARY.withCharset(this.charset))
                    );
                    case InputStream inputStream -> builder.addPart(
                        key,
                        new InputStreamBody(inputStream, ContentType.DEFAULT_BINARY.withCharset(this.charset))
                    );
                    case byte[] byteValue -> builder.addPart(
                        key,
                        new ByteArrayBody(byteValue, ContentType.DEFAULT_BINARY.withCharset(this.charset))
                    );
                    case Serializable serializableValue -> builder.addPart(
                        key,
                        new StringBody(serializableValue.toString(), ContentType.DEFAULT_TEXT.withCharset(this.charset))
                    );
                    case null, default -> throw new IllegalArgumentException("Invalid null type on key '" + key + "' and value '" + (value == null ? "null" : value.getClass()) + "'");
                }
            });

            return builder.build();
        }
    }
}
