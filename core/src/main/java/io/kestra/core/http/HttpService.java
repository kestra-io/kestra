package io.kestra.core.http;

import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpHeaders;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public abstract class HttpService {
    public static URI safeURI(HttpRequest request) {
        try {
            return request.getUri();
        } catch (URISyntaxException e1) {
            return URI.create(request.getRequestUri());
        }
    }

    public static HttpHeaders toHttpHeaders(@Nullable Header[] headers) {
        if (headers == null) {
            return null;
        }

        return HttpHeaders.of(
            Arrays.stream(headers)
                .collect(Collectors.groupingBy(
                        (header) -> header.getName().toLowerCase(Locale.ROOT),
                        Collectors.mapping(NameValuePair::getValue, Collectors.toList())
                    )
                ),
            (s, s2) -> true
        );
    }

    public static HttpEntityCopy copy(final HttpEntity entity) throws IOException {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        entity.writeTo(os);
        final byte[] body = os.toByteArray();
        ContentType contentType = ContentType.parse(entity.getContentType());
        boolean chunked = entity.isChunked();
        String contentEncoding = entity.getContentEncoding();

        final ByteArrayEntity copy = new ByteArrayEntity(body, contentType, contentEncoding, chunked);

        return new HttpEntityCopy(copy, body);
    }

    @RequiredArgsConstructor
    public static final class HttpEntityCopy implements HttpEntity {
        @Delegate
        private final HttpEntity entity;

        @Getter
        private final byte[] body;
    }
}
