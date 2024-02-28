package io.kestra.core.models.executions;

import io.kestra.core.models.TenantInterface;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import jakarta.validation.constraints.NotNull;

/**
 * The Kestra event for killing an execution. A {@link ExecutionKilled} can be in two states:
 * <p>
 * <pre>
 *  - {@link State#REQUESTED}: The event was requested either by an Executor or by an external request.
 *  - {@link State#EXECUTED}: The event was consumed and processed by the Executor.
 *  </pre>
 *
 *  A {@link ExecutionKilled} will always transit from {@link State#REQUESTED} to {@link State#EXECUTED}
 *  regardless of whether the associated execution exist or not to ensure that Workers will be notified for the tasks
 *  to be killed no matter what the circumstances.
 *  <p>
 *  IMPORTANT: A {@link ExecutionKilled} is considered to be a fire-and-forget event. As a result, we do not manage a
 *  COMPLETED state, i.e., the Executor will never wait for Workers to process an executed {@link ExecutionKilled}
 *  before considering an execution to be KILLED.
 */
@Value
@Builder
@EqualsAndHashCode
@ToString
public class ExecutionKilled implements TenantInterface {

    public enum State {
        REQUESTED,
        EXECUTED
    }

    /**
     * The state of this event.
     */
    State state;

    /**
     * The execution to be killed.
     */
    @NotNull
    String executionId;

    /**
     * Specifies whether killing the execution, also kill all sub-flow executions.
     */
    Boolean isOnKillCascade;

    /**
     * The tenant attached to the execution.
     */
    String tenantId;
}
