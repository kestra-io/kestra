package io.kestra.core.models.executions;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.TenantInterface;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

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
@Getter
@SuperBuilder
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ExecutionKilledExecution.class, name = "execution"),
    @JsonSubTypes.Type(value = ExecutionKilledTrigger.class, name = "trigger"),
})
abstract public class ExecutionKilled implements TenantInterface, HasUID {
    abstract public String getType();

    public enum State {
        REQUESTED,
        EXECUTED
    }

    /**
     * The state of this event.
     */
    State state;

    /**
     * The tenant attached to the execution.
     */
    protected String tenantId;
}
