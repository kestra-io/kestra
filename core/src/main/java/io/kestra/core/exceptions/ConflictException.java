package io.kestra.core.exceptions;

/**
 * General exception that can be thrown when a Kestra resource or entity conflicts with an existing one.
 * <p>
 * Typically used in REST API contexts to signal situations such as:
 * attempting to create a resource that already exists, or updating a resource
 * in a way that causes a conflict.
 * <p>
 * When propagated in the context of a REST API call, this exception should
 * result in an HTTP 409 Conflict response.
 */
public class ConflictException extends KestraRuntimeException {

    /**
     * Creates a new {@link ConflictException} instance.
     */
    public ConflictException() {
        super();
    }

    /**
     * Creates a new {@link ConflictException} instance.
     *
     * @param message the error message.
     */
    public ConflictException(final String message) {
        super(message);
    }
}
