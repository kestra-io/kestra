package io.kestra.plugin.core.state;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.tuple.Pair;

import java.net.URI;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Set a state in the state store.",
    description = "Values will be merged: \n" +
        "* If you provide a new key, the new key will be added.\n" +
        "* If you provide an existing key, the previous key will be overwrite.\n" +
        "\n" +
        "::alert{type=\"warning\"}\n" +
        "This method is not concurrency safe. If many executions for the same flow are concurrent, there is no guarantee on isolation on the value.\n" +
        "The value can be overwritten by other executions.\n" +
        "::\n"
)
@Plugin(
    examples = {
        @Example(
            title = "Set the default state for the current flow.",
            code = {
                "id: set_state",
                "type: io.kestra.plugin.core.state.Set",
                "data:",
                "  '{{ inputs.store }}': '{{ outputs.download.md5 }}'",
            },
            full = true
        ),
        @Example(
            title = "Set the `myState` state for the current flow.",
            code = {
                "id: set_state",
                "type: io.kestra.plugin.core.state.Set",
                "name: myState",
                "data:",
                "  '{{ inputs.store }}': '{{ outputs.download.md5 }}'",
            },
            full = true
        )
    },
    aliases = "io.kestra.core.tasks.states.Set"
)
public class Set extends AbstractState implements RunnableTask<Set.Output> {
    @Schema(
        title = "The data to be stored in the state store."
    )
    private Property<Map<String, Object>> data;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Pair<String, Map<String, Object>> dataRendered = this.merge(runContext, runContext.render(this.data).asMap(String.class, Object.class));

        return Output.builder()
            .count(dataRendered.getRight().size())
            .key(dataRendered.getLeft())
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
            title = "The key of the current state."
        )
        private final String key;
    }
}
