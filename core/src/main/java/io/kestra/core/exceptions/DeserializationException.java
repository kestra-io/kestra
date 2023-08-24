package io.kestra.core.exceptions;

import java.io.IOException;

public class DeserializationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private String record;

    public String getRecord() {
        return record;
    }

    public DeserializationException(IOException cause, String record) {
        super(cause);
        this.record = record;
    }

    public DeserializationException(String message) {
        super(message);
    }
}
