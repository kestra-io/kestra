package io.kestra.core.exceptions;

public class MissingRequiredArgument extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public MissingRequiredArgument(String message, Throwable e) {
        super(message, e);
    }

    public MissingRequiredArgument(String message) {
        super(message);
    }
}
