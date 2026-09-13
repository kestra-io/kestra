package io.kestra.core.exceptions;

import java.io.Serial;

/**
 * Raised when a flow is rejected by runtime governance and must not run.
 *
 * <p>
 * Never thrown by the open-source edition; editions enforcing governance rules throw it from
 * {@code FlowParsingService#parseForRuntime}. Callers react by type: the executor path surfaces it as a failed
 * execution, the scheduler path skips the flow's triggers.
 * </p>
 */
public class FlowBlockedException extends FlowProcessingException {

    @Serial
    private static final long serialVersionUID = 1L;

    public FlowBlockedException(String message) {
        super(message);
    }

    public FlowBlockedException(String message, Throwable cause) {
        super(message, cause);
    }
}
