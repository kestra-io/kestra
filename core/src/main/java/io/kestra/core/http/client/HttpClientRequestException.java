package io.kestra.core.http.client;

import java.io.Serial;

import io.kestra.core.http.HttpRequest;

import lombok.Getter;

@Getter
public class HttpClientRequestException extends HttpClientException {
    @Serial
    private static final long serialVersionUID = 1L;

    protected transient final HttpRequest request;

    public HttpClientRequestException(String message, HttpRequest request) {
        super(message);
        this.request = request;
    }

    public HttpClientRequestException(String message, HttpRequest request, Throwable cause) {
        super(message, cause);
        this.request = request;
    }
}
