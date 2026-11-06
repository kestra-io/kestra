package io.kestra.core.exceptions;

import java.io.Serial;
import java.util.List;

import io.kestra.core.scheduler.model.Blocker.Reason;

import lombok.Getter;

/**
 * Raised when a flow is rejected by runtime governance and must not run.
 *
 * <p>
 * Never thrown by the open-source edition; editions enforcing governance rules throw it from
 * {@code FlowParsingService#parseForRuntime}. Callers react by type: the executor path surfaces it as a failed
 * execution, the scheduler path skips the flow's triggers.
 * </p>
 */
@Getter
public class FlowBlockedException extends FlowProcessingException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Reason reason;
    private final List<String> policyIds;

    public FlowBlockedException(Reason reason, String message, List<String> policyIds, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.policyIds = List.copyOf(policyIds);
    }
}
