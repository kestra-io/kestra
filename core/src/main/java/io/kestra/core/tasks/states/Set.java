package io.kestra.core.tasks.states;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
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
    description = "Values will be added or modified for a given key:\n"
        + " * If you provide a new key, the new key-value pair will be added"
        + " * If you provide an existing key, the existing value for that key will be overwritten."
        + "\n\nNote that this method is **not -safe**. When many concurrent executions are trying to set"
        + " a value for the same key, there is **no transactional guarantee for such write operations**."
)
@Plugin(
    examples = {
        @Example(
            title = "Set the default state for the current flow",
            code = {
                "id: setState",
                "type: io.kestra.core.tasks.states.Set",
                "data:",
                "  '{{ inputs.store }}': '{{ outputs.download.md5 }}'",
            },
            full = true
        ),
        @Example(
            title = "Set the `myState` state for the current flow",
            code = {
                "id: setState",
                "type: io.kestra.core.tasks.states.Set",
                "name: myState",
                "data:",
                "  '{{ inputs.store }}': '{{ outputs.download.md5 }}'",
            },
            full = true
        )
    }
)
public class Set extends AbstractState implements RunnableTask<Set.Output> {
    @Schema(
        title = "The data to save into the state"
    )
    @PluginProperty(dynamic = true, additionalProperties = Object.class)
    private Map<String, Object> data;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Pair<URI, Map<String, Object>> data = this.merge(runContext, runContext.render(this.data));

        return Output.builder()
            .count(data.getRight().size())
            .uri(data.getLeft().toString())
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The count of properties found in the state"
        )
        private final int count;

        @Schema(
            title = "The uri of the current state"
        )
        private final String uri;
    }
}
