package io.kestra.core.secret;

import io.kestra.core.exceptions.KestraRuntimeException;

import java.io.Serial;

/**
 * Exception when a secret is not found.
 */
public class SecretNotFoundException extends KestraRuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SecretNotFoundException(String message) {
        super(message);
    }
}
