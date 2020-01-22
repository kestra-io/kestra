package org.kestra.core.exceptions;

public class InvalidFlowStateException extends IllegalArgumentException {
    public InvalidFlowStateException(String message) {
        super(message);
    }
}
