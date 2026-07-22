package io.kestra.webserver.exceptions;

import java.util.Objects;

import io.kestra.core.exceptions.ErrorCode;
import io.kestra.core.exceptions.HasErrorCode;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

/**
 * An {@link HttpStatusException} carrying a stable machine-readable {@link ErrorCode}, for code
 * paths (e.g. server filters) that respond with an HTTP status directly instead of throwing a
 * domain exception.
 */
public class KestraHttpStatusException extends HttpStatusException implements HasErrorCode {

    private final ErrorCode errorCode;

    /**
     * Creates a new {@link KestraHttpStatusException} instance.
     *
     * @param status the HTTP status of the response.
     * @param message the human-readable error message.
     * @param errorCode the stable machine-readable error code.
     */
    public KestraHttpStatusException(final HttpStatus status, final String message, final ErrorCode errorCode) {
        super(status, message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
