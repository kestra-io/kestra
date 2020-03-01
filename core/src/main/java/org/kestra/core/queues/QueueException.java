package org.kestra.core.queues;

public class QueueException extends RuntimeException {
    public QueueException(String message, Throwable e) {
        super(message, e);
    }
}
