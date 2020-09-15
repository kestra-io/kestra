package org.kestra.core.tasks.scripts;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.InputProperty;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.runners.RunContext;

import java.nio.file.Files;
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
    description = "Execute a Python script"
)
@Example(
    title = "Single python command",
    code = {
        "inputFiles:\n",
        "    main.py: |\n",
        "        import json\n",
        "        import requests\n",
        "        result = json.loads(open('data.json').read())\n",
        "        print(f\"python script {result['status']}\")\n",
        "        print(requests.get('http://google.com').status_code)\n",
        "    data.json: |\n",
        "       {\"status\": \"OK\"}\n",
        "    data.csv: {{ outputs.download.uri }}\n",
        "    pip.conf: |\n",
        "       # some specific pip repository configuration\n",
        "requirements:\n",
        "    - requests"
    }
)


public class Python extends Bash implements RunnableTask<Bash.Output> {


    @InputProperty(
        description = "The python interpreter to use",
        body = {
            "Set the python interpreter path to use"
        },
        dynamic = true
    )
    private String pythonPath = "/usr/bin/python3";

    @InputProperty(
        description = "Requirements are python dependencies to add to the python execution process",
        body = {
            "Python dependencies list to setup in the virtualenv"
        },
        dynamic = true
    )
    private String[] requirements;

    @Override
    public Bash.Output run(RunContext runContext) throws Exception {
        tmpFolder = Files.createTempDirectory("python-venv").toString();
        return run(runContext, throwFunction((tempFiles) -> {
            // final command
            List<String> renderer = new ArrayList<>();

            if (this.exitOnFailed) {
                renderer.add("set -o errexit");
            }

            String requirementsAsString = "";
            if (requirements != null) {
                requirementsAsString = "./bin/pip install " + runContext.render(String.join(" ", requirements)) + " > /dev/null";
            }

            if(!inputFiles.containsKey("main.py")) {
                throw new Exception("Invalid input files structure, expecting inputFiles property to contain at least a main.py key with python code value.");
            } else {
                this.handleInputFiles(runContext);
            }

            renderer.addAll(Arrays.asList(
                "rm -rf " + tmpFolder,
                pythonPath + " -m virtualenv " + tmpFolder + " > /dev/null",
                "mv " + tmpFilesFolder() + "/* " + tmpFolder,
                "cd " + tmpFolder,
                "./bin/pip install pip --upgrade > /dev/null",
                requirementsAsString,
                "./bin/python main.py"
            ));

            return String.join("\n", renderer);
        }));
    }

}
