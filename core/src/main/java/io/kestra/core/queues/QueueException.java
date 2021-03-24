package io.kestra.core.queues;

public class QueueException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public QueueException(String message, Throwable e) {
        super(message, e);
    }
}
