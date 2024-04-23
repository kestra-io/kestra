package io.kestra.core.models.executions;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.models.TenantInterface;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

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
abstract public class ExecutionKilled implements TenantInterface {
    abstract public String getType();

    abstract public String uid();

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
