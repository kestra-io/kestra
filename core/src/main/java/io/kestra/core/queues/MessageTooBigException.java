package io.kestra.core.queues;

public class MessageTooBigException extends QueueException {

    public MessageTooBigException(String message) {
        super(message);
    }
}
