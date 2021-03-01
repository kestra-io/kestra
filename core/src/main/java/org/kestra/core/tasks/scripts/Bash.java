package org.kestra.core.tasks.scripts;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.Plugin;
import org.kestra.core.models.annotations.PluginProperty;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.runners.RunContext;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import static org.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a Bash script, command or set of commands."
)
@Plugin(
    examples = {
        @Example(
            title = "Single bash command",
            code = {
                "commands:",
                "- echo \"The current execution is : {{execution.id}}\""
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
        )
    }
)
public class Bash extends AbstractBash implements RunnableTask<AbstractBash.Output> {
    @Schema(
        title = "The commands to run",
        description = "Default command will be launched with `/bin/sh -c \"commands\"`"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    protected String[] commands;

    @Override
    public AbstractBash.Output run(RunContext runContext) throws Exception {
        return run(runContext, throwFunction((additionalVars) -> {
            // final command
            List<String> renderer = new ArrayList<>();

            if (this.exitOnFailed) {
                renderer.add("set -o errexit");
                if (this.workingDirectory != null) {
                    renderer.add("cd " + this.workingDirectory.toAbsolutePath().toString());
                }
            }

            // renderer command
            for (String command : this.commands) {
                renderer.add(runContext.render(command, additionalVars));
            }

            return String.join("\n", renderer);
        }));
    }
}
