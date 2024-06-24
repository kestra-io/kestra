package io.kestra.core.exceptions;

import java.io.Serial;

public class InternalException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    public InternalException(Throwable e) {
        super(e);
    }

    public InternalException(String message) {
        super(message);
    }

    public InternalException(String message, Throwable e) {
        super(message, e);
    }
}
