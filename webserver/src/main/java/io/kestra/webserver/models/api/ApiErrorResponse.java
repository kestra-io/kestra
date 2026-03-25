package io.kestra.webserver.models.api;

import java.util.Objects;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;

public class ApiErrorResponse {

    private final String message;
    private final HttpStatus status;

    public ApiErrorResponse(HttpStatus status, String message) {
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.message = message;
    }

    public int getStatus() {
        return status.getCode();
    }

    public String getMessage() {
        return message;
    }

    public HttpResponse<ApiErrorResponse> toHttpResponse() {
        return HttpResponse.status(status).body(this);
    }
}
