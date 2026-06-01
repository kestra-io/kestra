package io.kestra.core.executor.command;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.kestra.core.events.EventId;
import io.kestra.core.models.executions.Execution;

import jakarta.annotation.Nullable;
import lombok.With;

/**
 * Command to replay an existing execution, optionally from a specific task run with new inputs.
 * The new execution is created with a pre-generated {@code executionId}, and the source execution
 * is identified by {@code sourceExecutionId}.
 */
public record Replay(
    String tenantId,
    String namespace,
    String flowId,
    String executionId,
    String sourceExecutionId,
    Instant timestamp,
    EventId eventId,

    @With @Nullable String operationId,
    @With @JsonProperty @Nullable String taskRunId,
    @With @JsonProperty @Nullable Integer revision,
    @With @JsonProperty @Nullable String breakpoints,
    @With @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> inputs
) implements ExecutionCommand {

    /**
     * Creates a {@code Replay} command from the given source execution and pre-generated new execution ID.
     *
     * @param sourceExecution the execution to replay
     * @param newExecutionId  pre-generated ID for the new execution
     * @param taskRunId       optional task run ID to restart from
     * @param revision        optional flow revision override
     * @param breakpoints     optional comma-separated breakpoint IDs
     */
    public static Replay from(Execution sourceExecution, String newExecutionId,
                              @Nullable String taskRunId, @Nullable Integer revision,
                              @Nullable String breakpoints) {
        return new Replay(
            sourceExecution.getTenantId(),
            sourceExecution.getNamespace(),
            sourceExecution.getFlowId(),
            newExecutionId,
            sourceExecution.getId(),
            Instant.now(),
            EventId.create(),
            null,
            taskRunId,
            revision,
            breakpoints,
            sourceExecution.getInputs()
        );
    }
}
