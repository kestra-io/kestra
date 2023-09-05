package io.kestra.core.exceptions;

import lombok.Getter;

import java.io.IOException;
import java.io.Serial;

@Getter
public class DeserializationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private String record;

    public DeserializationException(IOException cause, String record) {
        super(cause);
        this.record = record;
    }

    public DeserializationException(String message) {
        super(message);
    }
}
