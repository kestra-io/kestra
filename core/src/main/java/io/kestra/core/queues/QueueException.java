package io.kestra.core.queues;

import java.io.Serial;

public class QueueException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public QueueException(String message, Throwable e) {
        super(message, e);
    }
}
