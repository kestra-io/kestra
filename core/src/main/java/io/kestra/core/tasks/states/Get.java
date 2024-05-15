package io.kestra.core.tasks.states;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.FileNotFoundException;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get a state from the state store."
)
@Plugin(
    examples = {
        @Example(
            title = "Get the default state file for the current flow.",
            code = {
                "id: getState",
                "type: io.kestra.core.tasks.states.Get",
            },
            full = true
        ),
        @Example(
            title = "Get the `myState` state for the current flow.",
            code = {
                "id: getState",
                "type: io.kestra.core.tasks.states.Get",
                "name: myState",
            },
            full = true
        )
    },
    aliases = "io.kestra.core.tasks.states.Get"
)
public class Get extends AbstractState implements RunnableTask<Get.Output> {
    @Schema(
        title = "Raise an error if the state file is not found."
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private final Boolean errorOnMissing = false;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Map<String, Object> data;

        try {
            data = this.get(runContext);
        } catch (FileNotFoundException e) {
            if (this.errorOnMissing) {
                throw e;
            }

            data = Map.of();
        }

        return Output.builder()
            .count(data.size())
            .data(data)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The count of properties found in the state."
        )
        private final int count;

        @Schema(
            title = "The data extracted from the state."
        )
        private final Map<String, Object> data;
    }
}
