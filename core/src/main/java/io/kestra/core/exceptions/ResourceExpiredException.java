package io.kestra.core.exceptions;

import java.io.Serial;

public class ResourceExpiredException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceExpiredException(Throwable e) {
        super(e);
    }

    public ResourceExpiredException(String message) {
        super(message);
    }

    public ResourceExpiredException(String message, Throwable e) {
        super(message, e);
    }
}
