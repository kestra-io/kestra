package org.kestra.core.tasks.scripts;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.Plugin;
import org.kestra.core.models.annotations.PluginProperty;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.runners.RunContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.kestra.core.utils.Rethrow.throwFunction;

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
        "But you are also to add as many script as you need in `inputFiles`.\n" +
        "\n" +
        "You can also add a `pip.conf` in `inputFiles` to customize the pip download of dependencies (like a private registry)."
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a python script",
            code = {
                "inputFiles:\n",
                "  main.py: |\n",
                "    import json\n",
                "    import requests\n",
                "    import sys\n",
                "    result = json.loads(open(sys.argv[1]).read())\n",
                "    print(f\"python script {result['status']}\")\n",
                "    print(requests.get('http://google.com').status_code)\n",
                "  data.json: |\n",
                "    {\"status\": \"OK\"}\n",
                "  data.csv: {{ outputs.download.uri }}\n",
                "  pip.conf: |\n",
                "    # some specific pip repository configuration\n",
                "args:\n",
                "  - data.json\n",
                "requirements:\n",
                "  - requests"
            }
        )
    }
)
public class Python extends Bash implements RunnableTask<Bash.Output> {
    @Builder.Default
    @Schema(
        title = "The python interpreter to use",
        description = "Set the python interpreter path to use"
    )
    @PluginProperty(dynamic = true)
    private String pythonPath = "/usr/bin/python3";

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
    private String[] requirements;

    @Override
    public Bash.Output run(RunContext runContext) throws Exception {
        if (!inputFiles.containsKey("main.py")) {
            throw new Exception("Invalid input files structure, expecting inputFiles property to contain at least a main.py key with python code value.");
        }

        return run(runContext, throwFunction((additionalVars) -> {
            Path workingDirectory = this.tmpWorkingDirectory(additionalVars);

            // final command
            List<String> renderer = new ArrayList<>();

            if (this.exitOnFailed) {
                renderer.add("set -o errexit");
            }

            String requirementsAsString = "";
            if (requirements != null) {
                requirementsAsString = "./bin/pip install " + runContext.render(String.join(" ", requirements), additionalVars) + " > /dev/null";
            }

            String args = getArgs() == null ? "" : " " + runContext.render(String.join(" ", getArgs()), additionalVars);

            renderer.addAll(Arrays.asList(
                pythonPath + " -m virtualenv " + workingDirectory + " -p " + pythonPath + " > /dev/null",
                "cd " + workingDirectory,
                "./bin/pip install pip --upgrade > /dev/null",
                requirementsAsString,
                "./bin/python main.py" + args
            ));

            return String.join("\n", renderer);
        }));
    }
}
