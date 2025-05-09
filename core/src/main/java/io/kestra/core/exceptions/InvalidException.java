package io.kestra.core.exceptions;

import java.io.Serial;

/**
 * General exception that can be throws when a Kestra resource or entity is not valid.
 */
public class InvalidException extends KestraRuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Object invalid;

    /**
     * Creates a new {@link InvalidException} instance.
     *
     * @param invalid the invalid entity.
     * @param message the error message.
     */
    public InvalidException(final Object invalid, final String message) {
        super(message);
        this.invalid = invalid;
    }

    /**
     * Gets the invalid objects.
     *
     * @return the invalid object.
     */
    public Object invalidObject() {
        return invalid;
    }
}
