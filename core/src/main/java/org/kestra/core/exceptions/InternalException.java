package org.kestra.core.exceptions;

public class InternalException extends Exception {
    private static final long serialVersionUID = 1L;

    public InternalException(Throwable e) {
        super(e);
    }

    public InternalException(String message) {
        super(message);
    }
}
