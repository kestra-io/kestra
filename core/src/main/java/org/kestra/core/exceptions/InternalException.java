package org.kestra.core.exceptions;

public class InternalException extends Exception {
    public InternalException(Throwable e) {
        super(e);
    }

    public InternalException(String message) {
        super(message);
    }
}
