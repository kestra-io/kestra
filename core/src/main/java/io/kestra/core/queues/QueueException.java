package io.kestra.core.queues;

import java.io.Serial;

public class QueueException extends Exception {
    @Serial
    private static final long serialVersionUID = 2L;

    public QueueException(String message) {
        super(message);
    }

    public QueueException(String message, Throwable e) {
        super(message, e);
    }
}
