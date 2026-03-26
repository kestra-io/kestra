package io.kestra.core.exceptions;

import java.io.Serial;

import lombok.Getter;

@Getter
public class DeserializationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private String record;

    public DeserializationException(Exception cause, String record) {
        super(cause);
        this.record = record;
    }

    public DeserializationException(String message) {
        super(message);
    }
}
