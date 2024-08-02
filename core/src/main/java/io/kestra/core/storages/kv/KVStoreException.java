package io.kestra.core.storages.kv;

import io.kestra.core.exceptions.KestraRuntimeException;

import java.io.Serial;

/**
 * The base class for all other KVStore exceptions.
 */
public class KVStoreException extends KestraRuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public KVStoreException() {
    }

    public KVStoreException(String message) {
        super(message);
    }

    public KVStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public KVStoreException(Throwable cause) {
        super(cause);
    }
}
