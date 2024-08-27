package io.kestra.core.models.executions;

import io.kestra.plugin.core.trigger.Webhook;
import io.micronaut.core.annotation.Introspected;
import lombok.Builder;
import lombok.Value;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.triggers.AbstractTrigger;

import java.net.URI;
import java.util.Collections;
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

    URI logFile;

    public static ExecutionTrigger of(AbstractTrigger abstractTrigger, Output output) {
        return of(abstractTrigger, output, null);
    }

    public static ExecutionTrigger of(AbstractTrigger abstractTrigger, Output output, URI logFile) {
        return ExecutionTrigger.builder()
            .id(abstractTrigger.getId())
            .type(abstractTrigger.getType())
            .variables(output != null ? output.toMap() : Collections.emptyMap())
            .logFile(logFile)
            .build();
    }

    public static ExecutionTrigger of(AbstractTrigger abstractTrigger, Map<String, Object> variables) {
        return of(abstractTrigger, variables, null);
    }

    public static ExecutionTrigger of(AbstractTrigger abstractTrigger, Map<String, Object> variables, URI logFile) {
        return ExecutionTrigger.builder()
            .id(abstractTrigger.getId())
            .type(abstractTrigger.getType())
            .variables(variables)
            .logFile(logFile)
            .build();
    }
}
