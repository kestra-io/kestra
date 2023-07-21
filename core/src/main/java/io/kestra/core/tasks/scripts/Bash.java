package io.kestra.core.tasks.scripts;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import static io.kestra.core.utils.Rethrow.throwSupplier;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a Bash script, command or set of commands.",
    description = "This task is deprecated, please use the io.kestra.plugin.scripts.shell.Script or io.kestra.plugin.scripts.shell.Commands task instead.",
    deprecated = true
)
@Plugin(
    examples = {
        @Example(
            title = "Single bash command",
            code = {
                "commands:",
                "- 'echo \"The current execution is : {{execution.id}}\"'"
            }
        ),
        @Example(
            title = "Bash command that generate file in storage accessible through outputs",
            code = {
                "outputFiles:",
                "- first",
                "- second",
                "commands:",
                "- echo \"1\" >> {{ outputFiles.first }}",
                "- echo \"2\" >> {{ outputFiles.second }}"
            }
        ),
        @Example(
            title = "Bash with some inputs files",
            code = {
                "inputFiles:",
                "  script.sh: |",
                "    echo {{ workingDir }}",
                "commands:",
                "- /bin/bash script.sh",
            }
        ),
        @Example(
            title = "Bash with an input file from Kestra's local storage created by a previous task.",
            code = {
                "inputFiles:",
                "  data.csv: {{outputs.previousTaskId.uri}}",
                "commands:",
                "  - cat data.csv"
            }
        ),
        @Example(
            title = "Run a command on a docker image",
            code = {
                "runner: DOCKER",
                "dockerOptions:",
                "  image: php",
                "commands:",
                "- 'php -r 'print(phpversion() . \"\\n\");'",
            }
        ),
        @Example(
            title = "Execute cmd on windows",
            code = {
                "commands:",
                "  - 'echo \"The current execution is : {{execution.id}}\"'",
                "exitOnFailed: false",
                "interpreter: cmd",
                "interpreterArgs:",
                "  - /c",
            }
        ),
        @Example(
            title = "Set outputs from bash standard output",
            code = {
                "commands:",
                "  - echo '::{\"outputs\":{\"test\":\"value\",\"int\":2,\"bool\":true,\"float\":3.65}}::'",
            }
        ),
        @Example(
            title = "Send a counter metric from bash standard output",
            code = {
                "commands:",
                "  - echo '::{\"metrics\":[{\"name\":\"count\",\"type\":\"counter\",\"value\":1,\"tags\":{\"tag1\":\"i\",\"tag2\":\"win\"}}]}::'",
            }
        )
    }
)
@Deprecated
public class Bash extends AbstractBash implements RunnableTask<ScriptOutput> {
    @Schema(
        title = "The commands to run",
        description = "Default command will be launched with `/bin/sh -c \"commands\"`"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    protected String[] commands;

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        return run(runContext, throwSupplier(() -> {
            // final command
            List<String> renderer = new ArrayList<>();

            if (this.exitOnFailed) {
                renderer.add("set -o errexit");
            }

            // renderer command
            for (String command : this.commands) {
                renderer.add(runContext.render(command, additionalVars));
            }

            return String.join("\n", renderer);
        }));
    }
}
