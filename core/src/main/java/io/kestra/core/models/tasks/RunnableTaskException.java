package io.kestra.core.models.tasks;

import lombok.Getter;

/**
 * Exception that a {@link RunnableTask} can use to notice failure to the worker.
 * This exception can convey an Output that will be set inside the WorkerTaskResult.
 */
@Getter
public class RunnableTaskException extends Exception {
    private final Output output;

    public RunnableTaskException(Exception cause) {
        super(cause);
        this.output = null;
    }

    public RunnableTaskException(Exception cause, Output output) {
        super(cause);
        this.output = output;
    }

    public RunnableTaskException(String message) {
        super(message);
        this.output = null;
    }

    public RunnableTaskException(String message, Output output) {
        super(message);
        this.output = output;
    }

    public RunnableTaskException(String message, Throwable cause) {
        super(message, cause);
        this.output = null;
    }

    public RunnableTaskException(String message, Throwable cause, Output output) {
        super(message, cause);
        this.output = output;
    }
}
