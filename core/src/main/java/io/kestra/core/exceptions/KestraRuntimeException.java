package io.kestra.core.exceptions;

/**
 * The top-level {@link KestraRuntimeException} for non-recoverable errors.
 */
public class KestraRuntimeException extends RuntimeException {

    public KestraRuntimeException() {
    }

    public KestraRuntimeException(String message) {
        super(message);
    }

    public KestraRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public KestraRuntimeException(Throwable cause) {
        super(cause);
    }
}
