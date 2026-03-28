package io.kestra.core.exceptions;

import java.io.Serial;

import io.kestra.core.models.executions.Execution;

/**
 * Exception that can be thrown when a Flow is not found.
 */
public class FlowNotFoundException extends NotFoundException {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final String FLOW_NOT_FOUND_MESSAGE = "Unable to find flow %s.%s.%s revision %s for execution %s";

    /**
     * Creates a new {@link FlowNotFoundException} instance.
     */
    public FlowNotFoundException() {
        super();
    }

    /**
     * Creates a new {@link NotFoundException} instance.
     *
     * @param message the error message.
     */
    public FlowNotFoundException(final String message) {
        super(message);
    }

    public FlowNotFoundException(final Execution execution) {
        super(FLOW_NOT_FOUND_MESSAGE.formatted(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getFlowRevision(), execution.getId()));
    }
}
