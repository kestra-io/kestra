package io.kestra.core.exceptions;

/**
 * General exception that can be throws when a Kestra resource or entity is not found.
 */
public class NotFoundException extends KestraRuntimeException {

    /**
     * Creates a new {@link NotFoundException} instance.
     */
    public NotFoundException() {
        super();
    }

    /**
     * Creates a new {@link NotFoundException} instance.
     *
     * @param message the error message.
     */
    public NotFoundException(final String message) {
        super(message);
    }
}
