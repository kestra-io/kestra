package io.kestra.plugin.core.execution;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.ExecutionUpdatableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.MapUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;
import java.util.Collections;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Allow to set execution variables. These variables are available via the `{{ vars.name }}` expression."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = "Set variables",
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
                    message: "{{ vars.message }} {{ vars.name }}\""""
        )
    }
)
public class SetVariables extends Task implements ExecutionUpdatableTask {
    @Schema(title = "The variables")
    @NotNull
    private Property<Map<String, Object>> variables;

    @Schema(title = "Flag specifying whether to overwrite existing variables")
    @NotNull
    @Builder.Default
    private Property<Boolean> overwrite = Property.ofValue(true);

    @Override
    public Execution update(Execution execution, RunContext runContext) throws Exception {
        Map<String, Object> renderedVars = runContext.render(this.variables).asMap(String.class, Object.class);
        boolean renderedOverwrite = runContext.render(overwrite).as(Boolean.class).orElseThrow();

        Map<String, Object> currentVariables =
            execution.getVariables() == null ? Collections.emptyMap() : execution.getVariables();

        if (!renderedOverwrite) {
            // check that none of the new variables already exist
            List<String> duplicated = renderedVars.keySet().stream()
                .filter(currentVariables::containsKey)
                .toList();

            if (!duplicated.isEmpty()) {
                throw new IllegalArgumentException(
                    "`overwrite` is set to false and the following variables already exist: " +
                    String.join(",", duplicated)
                );
            }
        }

        return execution.withVariables(MapUtils.deepMerge(currentVariables, renderedVars));
    }
}
