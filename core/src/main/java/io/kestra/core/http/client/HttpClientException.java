package io.kestra.core.http.client;

import java.io.Serial;

import org.apache.hc.core5.http.HttpException;

import lombok.Getter;

@Getter
public abstract class HttpClientException extends HttpException {
    @Serial
    private static final long serialVersionUID = 1L;

    public HttpClientException(String message) {
        super(message);
    }

    public HttpClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
