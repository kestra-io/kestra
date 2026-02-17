package io.kestra.core.exceptions;

/**
 * Exception that can be thrown when a Flow is not found.
 */
public class FlowNotFoundException extends NotFoundException {

    /**
     * Creates a new {@link FlowNotFoundException} instance.
     */
    public FlowNotFoundException() {
        super();
    }

    /**
     * Creates a new {@link NotFoundException} instance.
     *
     * @param message the error message.
     */
    public FlowNotFoundException(final String message) {
        super(message);
    }
}
