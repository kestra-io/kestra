package io.kestra.core.exceptions;

import java.io.Serial;

public class ResourceAccessDeniedException extends KestraRuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceAccessDeniedException() {
    }

    public ResourceAccessDeniedException(String message) {
        super(message);
    }
}
