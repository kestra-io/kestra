package io.kestra.core.http.client;

import io.kestra.core.http.HttpRequest;
import lombok.Getter;

import java.io.Serial;

@Getter
public class HttpClientRequestException extends HttpClientException {
    @Serial
    private static final long serialVersionUID = 1L;

    protected final HttpRequest request;

    public HttpClientRequestException(String message, HttpRequest request) {
        super(message);
        this.request = request;
    }

    public HttpClientRequestException(String message, HttpRequest request, Throwable cause) {
        super(message, cause);
        this.request = request;
    }
}
