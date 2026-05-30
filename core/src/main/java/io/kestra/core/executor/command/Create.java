package io.kestra.core.executor.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.kestra.core.debug.Breakpoint;
import io.kestra.core.events.EventId;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.ExecutionId;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.State;
import io.kestra.core.test.flow.TaskFixture;
import io.kestra.core.utils.IdUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import lombok.With;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public record Create(
    ExecutionId executionFullId,
    Instant timestamp,
    EventId eventId,

    @With @Nullable String operationId,
    @With @JsonProperty @Nullable State.Type stateType,
    @With @JsonProperty @Nullable ExecutionKind kind,
    @With @JsonProperty @Nullable ExecutionTrigger trigger,
    @With @JsonProperty @Nullable List<Label> labels,
    @With @JsonProperty @Nullable Instant scheduleDate,
    @With @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable @Schema(implementation = Object.class) Map<String, Object> inputs,
    @With @JsonProperty @Nullable List<Breakpoint> breakpoints,
    @With @JsonProperty @Nullable String traceParent,
    @With @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable List<TaskFixture> fixtures,
    @With @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable @Schema(implementation = Object.class) Map<String, Object> variables
) implements ExecutionCommand {
    public static Create of(FlowId flowId) {
        return new Create(
            new  ExecutionId(flowId, IdUtils.create()),
            Instant.now(),
            EventId.create(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
    public static Create of(ExecutionId executionId) {
        return new Create(
            executionId,
            Instant.now(),
            EventId.create(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    public Create withInputsFromReader(Function<String, Map<String, Object>> inputsReader) {
        var inputs = inputsReader.apply(this.executionId());
        return new Create(executionFullId, timestamp, eventId, operationId, stateType, kind, trigger, labels, scheduleDate, inputs, breakpoints, traceParent, fixtures, variables);
    }

    @Override
    public String tenantId() {
        return executionFullId().tenantId();
    }

    @Override
    public String namespace() {
        return executionFullId().namespace();
    }

    @Override
    public String flowId() {
        return executionFullId().flowId();
    }

    @Override
    public String executionId() {
        return executionFullId().executionId();
    }

    public Integer flowRevision() {
        return executionFullId().flowRevision();
    }
}
