package io.kestra.webserver.services;


import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.micronaut.http.HttpStatus;

import java.io.InputStream;
import java.net.http.HttpHeaders;

public abstract class MicronautHttpService {

    public static HttpRequest from(io.micronaut.http.HttpRequest<?> request) {
        HttpRequest.RequestBody body = null;
        if (request.getBody().isPresent()) {
            Object bodyContent = request.getBody().get();

            if (bodyContent instanceof InputStream inputStream) {
                body = HttpRequest.InputStreamRequestBody.builder()
                    .content(inputStream)
                    .build();
            } else if (bodyContent instanceof byte[] bytes) {
                body = HttpRequest.ByteArrayRequestBody.builder()
                    .content(bytes)
                    .build();
            } else if (bodyContent instanceof String str) {
                body = HttpRequest.StringRequestBody.builder()
                    .content(str)
                    .build();
            } else {
                body = HttpRequest.JsonRequestBody.builder()
                    .content(bodyContent)
                    .build();
            }
        }

        return HttpRequest.builder()
            .uri(request.getUri())
            .method(request.getMethod().name())
            .body(body)
            .headers(HttpHeaders.of(request.getHeaders().asMap(), (a, b) -> true))
            .remoteAddress(request.getRemoteAddress())
            .build();
    }


    public static <T> io.micronaut.http.HttpResponse<?> to(HttpResponse<T> response) {
        var result = io.micronaut.http.HttpResponse
            .status(HttpStatus.valueOf(response.getStatus().getCode()))
            .headers(headers -> {
                if (response.getHeaders() != null) {
                    response.getHeaders().map().forEach((key, values) -> {
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