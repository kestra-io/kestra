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
            title = "Delete the default state for the current flow",
            code = {
                "id: getState",
                "type: io.kestra.core.tasks.states.Delete",
            },
            full = true
        ),
        @Example(
            title = "Delete the `myState` state for the current flow",
            code = {
                "id: getState",
                "type: io.kestra.core.tasks.states.Delete",
                "name: myState",
            },
            full = true
        )
    }
)
public class Delete extends AbstractState implements RunnableTask<Delete.Output> {
    @Schema(
        title = "raise an error if the state is not found"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private final Boolean errorOnMissing = false;

    @Override
    public Output run(RunContext runContext) throws Exception {

        boolean delete = this.delete(runContext);

        if (errorOnMissing && !delete) {
            throw new FileNotFoundException("Unable to find file '" + runContext.render(this.name) + "'");
        }

        return Output.builder()
            .deleted(delete)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "If the files was really deleted"
        )
        private final Boolean deleted;
    }
}
