package org.kestra.core.exceptions;

public class MissingRequiredInput extends IllegalArgumentException {
    public MissingRequiredInput(String message, Throwable e) {
        super(message, e);
    }

    public MissingRequiredInput(String message) {
        super(message);
    }
}
