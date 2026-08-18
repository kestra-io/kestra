package io.kestra.core.exceptions;

/**
 * General exception that can be thrown when a Kestra resource or entity exists but the caller is not allowed to
 * perform the requested operation on it.
 * <p>
 * When propagated in the context of a REST API call, this exception should result in an HTTP 403 Forbidden
 * response.
 */
public class ForbiddenException extends KestraRuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new {@link ForbiddenException} instance.
     *
     * @param message the error message.
     */
    public ForbiddenException(final String message) {
        super(message);
    }
}
