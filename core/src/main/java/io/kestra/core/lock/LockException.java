package io.kestra.core.lock;

import io.kestra.core.exceptions.KestraException;

public class LockException extends KestraException {
    public LockException(String message) {
        super(message);
    }

    public LockException(Throwable cause) {
        super(cause);
    }
}
