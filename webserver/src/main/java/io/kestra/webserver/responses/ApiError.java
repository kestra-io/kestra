package io.kestra.webserver.responses;

import java.util.Objects;

import io.kestra.core.exceptions.ErrorCode;

import io.micronaut.http.hateoas.JsonError;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A {@link JsonError} carrying a stable machine-readable {@link ErrorCode} in addition to the
 * human-readable message, so that API clients can branch on the error type without parsing
 * message text.
 */
@Schema(description = "An API error response carrying a stable machine-readable code in addition to the human-readable message.")
public class ApiError extends JsonError {

    private final ErrorCode code;

    /**
     * Creates a new {@link ApiError} instance.
     *
     * @param code the stable machine-readable error code.
     * @param message the human-readable error message.
     */
    public ApiError(final ErrorCode code, final String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    /**
     * @return the stable machine-readable code identifying the error.
     */
    @Schema(description = "The stable machine-readable code identifying the error.")
    public ErrorCode getCode() {
        return code;
    }
}
