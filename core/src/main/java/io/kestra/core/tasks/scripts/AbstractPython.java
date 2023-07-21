package io.kestra.core.tasks.scripts;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Slf4j
@Deprecated
public abstract class AbstractPython extends AbstractBash {
    @Builder.Default
    @Schema(
        title = "The python interpreter to use",
        description = "Set the python interpreter path to use"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    private final String pythonPath = "python";

    @Schema(
        title = "Python command args",
        description = "Arguments list to pass to main python script"
    )
    @PluginProperty(dynamic = true)
    private List<String> args;

    @Schema(
        title = "Requirements are python dependencies to add to the python execution process",
        description = "Python dependencies list to setup in the virtualenv, in the same format than requirements.txt"
    )
    @PluginProperty(dynamic = true)
    protected List<String> requirements;

    protected String virtualEnvCommand(RunContext runContext, List<String> requirements) throws IllegalVariableEvaluationException {
        List<String> renderer = new ArrayList<>();

        if (this.exitOnFailed) {
            renderer.add("set -o errexit");
        }
        renderer.add(this.pythonPath + " -m venv --system-site-packages " + workingDirectory + " > /dev/null");

        if (requirements != null) {
            renderer.addAll(Arrays.asList(
                "./bin/pip install pip --upgrade > /dev/null",
                "./bin/pip install " + runContext.render(String.join(" ", requirements), additionalVars) + " > /dev/null"));
        }

        return String.join("\n", renderer);
    }

    @Override
    protected Map<String, String> finalEnv() throws IOException {
        Map<String, String> env = super.finalEnv();

        // python buffer log by default, so we force unbuffer to have the whole log
        env.put("PYTHONUNBUFFERED", "true");

        return env;
    }
}
