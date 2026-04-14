package io.kestra.core.exceptions;

public class InvalidTriggerConfigurationException extends KestraRuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidTriggerConfigurationException() {
        super();
    }

    public InvalidTriggerConfigurationException(String message) {
        super(message);
    }

    public InvalidTriggerConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
