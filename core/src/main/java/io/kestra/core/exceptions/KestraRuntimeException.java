package io.kestra.core.exceptions;

import java.io.Serial;

/**
 * The top-level {@link KestraRuntimeException} for non-recoverable errors.
 */
public class KestraRuntimeException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

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
