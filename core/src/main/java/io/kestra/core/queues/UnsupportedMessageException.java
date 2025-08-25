package io.kestra.core.queues;

import java.io.Serial;

public class UnsupportedMessageException extends QueueException {
    @Serial
    private static final long serialVersionUID = 1L;

    public UnsupportedMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
