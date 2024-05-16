package io.kestra.plugin.core.flow;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Run tasks sequentially sharing the same filesystem",
    description = "This task is deprecated, please use the io.kestra.core.tasks.flows.WorkingDirectory task instead."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = {
                "id: worker",
                "namespace: io.kestra.tests",
                "",
                "tasks:",
                "  - id: worker",
                "    type: io.kestra.core.tasks.flows.Worker",
                "    tasks:",
                "      - id: first",
                "        type: io.kestra.plugin.scripts.shell.Commands",
                "        commands:",
                "        - 'echo \"{{ taskrun.id }}\" > {{ workingDir }}/stay.txt'",
                "      - id: second",
                "        type: io.kestra.plugin.scripts.shell.Commands",
                "        commands:",
                "        - |",
                "          echo '::{\"outputs\": {\"stay\":\"'$(cat {{ workingDir }}/stay.txt)'\"}}::'"
            }
        )
    },
    aliases = "io.kestra.core.tasks.flows.Worker"
)
@Deprecated
public class Worker extends WorkingDirectory {}
