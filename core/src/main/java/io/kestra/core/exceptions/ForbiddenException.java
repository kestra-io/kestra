package io.kestra.core.exceptions;

import java.io.Serial;

/**
 * General exception that can be thrown when access to a Kestra resource or operation is denied.
 */
public class ForbiddenException extends KestraRuntimeException implements HasErrorCode {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new {@link ForbiddenException} instance.
     */
    public ForbiddenException() {
        super();
    }

    /**
     * Creates a new {@link ForbiddenException} instance.
     *
     * @param message the error message.
     */
    public ForbiddenException(final String message) {
        super(message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.FORBIDDEN;
    }
}
