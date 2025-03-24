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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete a state from the state store."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete the default state for the current flow.",
            code = {
                "id: delete_state",
                "type: io.kestra.plugin.core.state.Delete",
            },
            full = true
        ),
        @Example(
            title = "Delete the `myState` state for the current flow.",
            code = {
                "id: delete_state",
                "type: io.kestra.plugin.core.state.Delete",
                "name: myState",
            },
            full = true
        )
    },
    aliases = "io.kestra.core.tasks.states.Delete"
)
public class Delete extends AbstractState implements RunnableTask<Delete.Output> {
    @Schema(
        title = "Raise an error if the state is not found."
    )
    @Builder.Default
    private final Property<Boolean> errorOnMissing = Property.of(false);

    @Override
    public Output run(RunContext runContext) throws Exception {

        boolean delete = this.delete(runContext);

        if (Boolean.TRUE.equals(runContext.render(errorOnMissing).as(Boolean.class).orElseThrow()) && !delete) {
            throw new FileNotFoundException("Unable to find the state file '" + runContext.render(this.name).as(String.class).orElseThrow() + "'");
        }

        return Output.builder()
            .deleted(delete)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Whether the state file was deleted."
        )
        private final Boolean deleted;
    }
}
