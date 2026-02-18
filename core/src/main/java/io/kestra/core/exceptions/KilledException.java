package io.kestra.core.exceptions;

import java.io.Serial;

/**
 * Exception thrown when a task runner is killed during execution.
 */
public class KilledException extends KestraRuntimeException {
    private static final String DEFAULT_MESSAGE = "Task runner was killed during execution.";
    private static final String PHASE_MESSAGE = "Task runner was killed during %s.";

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
     * @param phase a short description of the execution phase
     *              (e.g. "image pull", "container start").
     */
    public KilledException(String phase) {
        super(PHASE_MESSAGE.formatted(phase));
    }
}
