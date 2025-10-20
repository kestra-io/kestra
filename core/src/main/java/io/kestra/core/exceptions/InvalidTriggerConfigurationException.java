package io.kestra.core.exceptions;

public class InvalidTriggerConfigurationException extends KestraRuntimeException {
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
