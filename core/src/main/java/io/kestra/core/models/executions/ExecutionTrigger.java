package io.kestra.core.models.executions;

import io.micronaut.core.annotation.Introspected;
import lombok.Builder;
import lombok.Value;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.triggers.AbstractTrigger;

import java.util.Map;
import jakarta.validation.constraints.NotNull;

@Value
@Builder
@Introspected
public class ExecutionTrigger {
    @NotNull
    String id;

    @NotNull
    String type;

    Map<String, Object> variables;

    public static ExecutionTrigger of(AbstractTrigger abstractTrigger, Output output) {
        return ExecutionTrigger.builder()
            .id(abstractTrigger.getId())
            .type(abstractTrigger.getType())
            .variables(output.toMap())
            .build();
    }

    public static ExecutionTrigger of(AbstractTrigger abstractTrigger, Map<String, Object> variables) {
        return ExecutionTrigger.builder()
            .id(abstractTrigger.getId())
            .type(abstractTrigger.getType())
            .variables(variables)
            .build();
    }
}
