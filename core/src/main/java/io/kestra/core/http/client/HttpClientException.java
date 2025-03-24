package io.kestra.core.http.client;

import lombok.Getter;
import org.apache.hc.core5.http.HttpException;

import java.io.Serial;

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
