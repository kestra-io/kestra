package io.kestra.core.lock;

import io.kestra.core.exceptions.KestraRuntimeException;

public class LockException extends KestraRuntimeException {
    private static final long serialVersionUID = 1L;

    public LockException(String message) {
        super(message);
    }

    public LockException(Throwable cause) {
        super(cause);
    }
}
