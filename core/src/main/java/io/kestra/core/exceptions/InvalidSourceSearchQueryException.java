package io.kestra.core.exceptions;

import java.io.Serial;

public class InvalidSourceSearchQueryException extends KestraRuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidSourceSearchQueryException(final String message) {
        super(message);
    }
}
