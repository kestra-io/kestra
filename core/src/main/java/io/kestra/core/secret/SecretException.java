package io.kestra.core.secret;

import java.io.Serial;

import io.kestra.core.exceptions.KestraRuntimeException;

/**
 * Top-level exception for Secrets.
 */
public class SecretException extends KestraRuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SecretException(String message) {
        super(message);
    }
}
