package org.kestra.core.models.executions;

import io.micronaut.core.annotation.Introspected;
import lombok.Builder;
import lombok.Value;
import org.kestra.core.models.tasks.Output;
import org.kestra.core.models.triggers.AbstractTrigger;

import java.util.Map;
import javax.validation.constraints.NotNull;

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
}
