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
@Schema(
    title = "Execute a Python script",
    description = "With this Python task, we can execute a full python script.\n" +
        "The task will create a fresh `virtualenv` for every tasks and allow you to install some python package define in `requirements` property.\n" +
        "\n" +
        "By convention, you need to define at least a `main.py` files in `inputFiles` that will be the script used.\n" +
        "But you are also able to add as many script as you need in `inputFiles`.\n" +
        "\n" +
        "You can also add a `pip.conf` in `inputFiles` to customize the pip download of dependencies (like a private registry).\n" +
        "\n" +
        "You can send outputs & metrics from your python script that can be used by others tasks. In order to help, we inject a python package directly on the working dir." +
        "Here is an example usage:\n" +
        "```python\n" +
        "from kestra import Kestra\n" +
        "Kestra.outputs({'test': 'value', 'int': 2, 'bool': True, 'float': 3.65})\n" +
        "Kestra.counter('count', 1, {'tag1': 'i', 'tag2': 'win'})\n" +
        "Kestra.timer('timer1', lambda: time.sleep(1), {'tag1': 'i', 'tag2': 'lost'})\n" +
        "Kestra.timer('timer2', 2.12, {'tag1': 'i', 'tag2': 'destroy'})\n" +
        "```"
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a python script",
            code = {
                "inputFiles:",
                "  data.json: |",
                "          {\"status\": \"OK\"}",
                "  main.py: |",
                "    from kestra import Kestra",
                "    import json",
                "    import requests",
                "    import sys",
                "    result = json.loads(open(sys.argv[1]).read())",
                "    print(f\"python script {result['status']}\")",
                "    response = requests.get('http://google.com')",
                "    print(response.status_code)",
                "    Kestra.outputs({'status': response.status_code, 'text': response.text})",
                "  pip.conf: |",
                "    # some specific pip repository configuration",
                "args:",
                "  - data.json",
                "requirements:",
                "  - requests"
            }
        )
    }
)
@Slf4j
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
