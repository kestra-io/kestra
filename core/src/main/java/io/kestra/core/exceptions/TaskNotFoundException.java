package io.kestra.core.exceptions;

/**
 * Exception that can be thrown when a task is not found.
 */
public class TaskNotFoundException extends NotFoundException {

    /**
     * Creates a new {@link TaskNotFoundException} instance.
     */
    public TaskNotFoundException() {
        super();
    }

    /**
     * Creates a new {@link NotFoundException} instance.
     *
     * @param message the error message.
     */
    public TaskNotFoundException(final String message) {
        super(message);
    }
}
