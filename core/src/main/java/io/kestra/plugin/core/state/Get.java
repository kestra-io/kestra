package io.kestra.plugin.core.state;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
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
                "id: get_state",
                "type: io.kestra.plugin.core.state.Get",
            },
            full = true
        ),
        @Example(
            title = "Get the `myState` state for the current flow.",
            code = {
                "id: get_state",
                "type: io.kestra.plugin.core.state.Get",
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
    @Builder.Default
    private final Property<Boolean> errorOnMissing = Property.of(false);

    @Override
    public Output run(RunContext runContext) throws Exception {
        Map<String, Object> data;

        try {
            data = this.get(runContext);
        } catch (FileNotFoundException e) {
            if (Boolean.TRUE.equals(runContext.render(this.errorOnMissing).as(Boolean.class).orElseThrow())) {
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
