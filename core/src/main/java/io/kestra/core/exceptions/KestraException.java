package io.kestra.core.exceptions;

import java.io.Serial;

/**
 * The top-level {@link KestraException}..
 */
public class KestraException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    public KestraException() {
    }

    public KestraException(String message) {
        super(message);
    }

    public KestraException(String message, Throwable cause) {
        super(message, cause);
    }

    public KestraException(Throwable cause) {
        super(cause);
    }

}
