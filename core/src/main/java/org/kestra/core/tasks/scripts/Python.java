package org.kestra.core.tasks.scripts;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.commons.io.FileUtils;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.InputProperty;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.runners.RunContext;
import org.kestra.core.tasks.debugs.Return;
import org.slf4j.Logger;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.kestra.core.utils.Rethrow.*;

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
        "inputFiles:",
        "    main.py: |",
        "        import json",
        "        import requests",
        "        result = json.loads(open('data.json').read())",
        "        print(f\"python script {result['status']}\")",
        "        print(requests.get('http://google.com').status_code)",
        "    data.json: |",
        "       {\"status\": \"OK\"}",
        "    pip.conf: |",
        "       # some specific pip repository configuration",
        "requirements:",
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
        tmpFolder = Files.createTempDirectory("/tmp/").toString();
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
