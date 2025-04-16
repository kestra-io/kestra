package io.kestra.core.exceptions;

import java.io.Serial;

/**
 * Exception class for all problems encountered when processing (parsing, injecting defaults, validating) a flow.
 */
public class FlowProcessingException extends KestraException {

    @Serial
    private static final long serialVersionUID = 1L;
    
    public FlowProcessingException(String message) {
        super(message);
    }

    public FlowProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public FlowProcessingException(Throwable cause) {
        super(cause);
    }
}
