package io.kestra.core.exceptions;

/**
 * Exception thrown when a task runner is killed during execution.
 */
public class KilledException extends KestraRuntimeException {
    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_MESSAGE = "Execution was killed.";

    /**
     * Creates a new {@link KilledException} with a default message.
     */
    public KilledException() {
        super(DEFAULT_MESSAGE);
    }

    /**
     * Creates a new {@link KilledException} with a message describing
     * the execution phase during which the kill occurred.
     *
     * @param message the error message.
     */
    public KilledException(String message) {
        super(message);
    }
}
