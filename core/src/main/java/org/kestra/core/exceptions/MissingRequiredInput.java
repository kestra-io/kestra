package org.kestra.core.exceptions;

public class MissingRequiredInput extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public MissingRequiredInput(String message, Throwable e) {
        super(message, e);
    }

    public MissingRequiredInput(String message) {
        super(message);
    }
}
