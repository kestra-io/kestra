package io.kestra.core.queues;

import java.io.Serial;

public class MessageTooBigException extends QueueException {
    @Serial
    private static final long serialVersionUID = 1L;

    public MessageTooBigException(String message) {
        super(message);
    }
}
