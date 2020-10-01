package org.kestra.core.tasks.scripts;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.InputProperty;
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
@Documentation(
    description = "Execute a Python script",
    body = {
        "With this Python task, we can execute a full python script.",
        "The task will create a fresh `virtualenv` for every tasks and allow you to install some python package define in `requirements` property.",
        "",
        "By convention, you need to define at least a `main.py` files in `inputFiles` that will be the script used.",
        "But you are also to add as many script as you need in `inputFiles`.",
        "",
        "You can also add a `pip.conf` in `inputFiles` to customize the pip download of dependencies (like a private registry).",
    }
)
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
public class Python extends Bash implements RunnableTask<Bash.Output> {
    @Builder.Default
    @InputProperty(
        description = "The python interpreter to use",
        body = {
            "Set the python interpreter path to use"
        },
        dynamic = true
    )
    private String pythonPath = "/usr/bin/python3";

    @InputProperty(
        description = "Python command args",
        body = {
            "Arguments list to pass to main python script"
        },
        dynamic = true
    )
    private List<String> args;

    @InputProperty(
        description = "Requirements are python dependencies to add to the python execution process",
        body = {
            "Python dependencies list to setup in the virtualenv, in the same format than requirements.txt"
        },
        dynamic = true
    )
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
