package io.kestra.plugin.core.execution;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ExecutionUpdatableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Unset execution variables."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "Set and later unset variables",
            code = """
                id: variables
                namespace: company.team

                variables:
                  name: World

                tasks:
                  - id: set_vars
                    type: io.kestra.plugin.core.execution.SetVariables
                    variables:
                      message: Hello
                      name: Loïc
                  - id: hello
                    type: io.kestra.plugin.core.log.Log
                    message: "{{ vars.message }} {{ vars.name }}"
                  - id: unset_variables
                    type: io.kestra.plugin.core.execution.UnsetVariables
                    variables:
                      - message
                      - name"""
        )
    }
)
public class UnsetVariables extends Task implements ExecutionUpdatableTask {
    @Schema(title = "The variables")
    @NotNull
    private Property<List<String>> variables;

    @Schema(title = "Flag specifying whether to ignore missing variables")
    @NotNull
    @Builder.Default
    private Property<Boolean> ignoreMissing = Property.ofValue(false);


    @Override
    public Execution update(Execution execution, RunContext runContext) throws Exception {
        List<String> renderedVariables = runContext.render(variables).asList(String.class);
        boolean renderedIgnoreMissing = runContext.render(ignoreMissing).as(Boolean.class).orElseThrow();
        Map<String, Object> variables = execution.getVariables();
        for (String key : renderedVariables) {
            removeVar(variables, key, renderedIgnoreMissing);
        }
        return execution.withVariables(variables);
    }

    private void removeVar(Map<String, Object> vars, String key, boolean ignoreMissing) {
        if (key.indexOf('.') >= 0) {
            String prefix = key.substring(0, key.indexOf('.'));
            String suffix = key.substring(key.indexOf('.') + 1);
            removeVar((Map<String, Object>) vars.get(prefix), suffix, ignoreMissing);
        } else {
            if (ignoreMissing && !vars.containsKey(key)) {
                return;
            }
            vars.remove(key);
        }
    }
}
