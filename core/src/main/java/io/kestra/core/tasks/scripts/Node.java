package io.kestra.core.tasks.scripts;

import com.google.common.base.Charsets;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a Node.js script",
    description = "With this Node task, we can execute a full javascript script.\n" +
        "The task will create a temprorary folder for every tasks and allows you to install some npm packages defined in an optional `package.json` file.\n" +
        "\n" +
        "By convention, you need to define at least a `main.js` files in `inputFiles` that will be the script used.\n" +
        "You can also  add as many javascript files as you need in `inputFiles`.\n" +
        "\n" +
        "You can send outputs & metrics from your node script that can be used by others tasks. In order to help, we inject a node package directly on the working dir." +
        "Here is an example usage:\n" +
        "```javascript\n" +
        "const Kestra = require(\"./kestra\");\n" +
        "Kestra.outputs({test: 'value', int: 2, bool: true, float: 3.65});\n" +
        "Kestra.counter('count', 1, {tag1: 'i', tag2: 'win'});\n" +
        "Kestra.timer('timer1', (callback) => { setTimeout(callback, 1000) }, {tag1: 'i', tag2: 'lost'});\n" +
        "Kestra.timer('timer2', 2.12, {tag1: 'i', tag2: 'destroy'});\n" +
        "```"
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a node script",
            code = {
                "inputFiles:",
                "  main.js: |",
                "    const Kestra = require(\"./kestra\");",
                "    const fs = require('fs')",
                "    const result = fs.readFileSync(process.argv[2], \"utf-8\")",
                "    console.log(JSON.parse(result).status)",
                "    const axios = require('axios')",
                "    axios.get('http://google.fr').then(d => console.log(d.status); Kestra.outputs({'status': d.status, 'text': d.data}))",
                "    console.log(require('./mymodule').value)",
                "  data.json: |",
                "    {\"status\": \"OK\"}",
                "  mymodule.js: |",
                "    module.exports.value = 'hello world'",
                "  package.json: |",
                "    {",
                "      \"name\": \"tmp\",",
                "      \"version\": \"1.0.0\",",
                "      \"description\": \"\",",
                "      \"main\": \"index.js\",",
                "      \"dependencies\": {",
                "          \"axios\": \"^0.20.0\"",
                "      },",
                "      \"devDependencies\": {},",
                "      \"scripts\": {",
                "          \"test\": \"echo \"Error: no test specified\" && exit 1\"",
                "      },",
                "      \"author\": \"\",",
                "      \"license\": \"ISC\"",
                "    }",
                "args:",
                "  - data.json",
            }
        )
    }
)
public class Node extends AbstractBash implements RunnableTask<AbstractBash.Output> {
    @Builder.Default
    @Schema(
        title = "The node interpreter to use",
        description = "Set the node interpreter path to use"
    )
    private final String nodePath = "/usr/bin/node";

    @Builder.Default
    @Schema(
        title = "The npm binary to use",
        description = "Set the npm binary path for node dependencies setup"
    )
    private final String npmPath = "/usr/bin/npm";

    @Schema(
        title = "node command args",
        description = "Arguments list to pass to main javascript script"

    )
    @PluginProperty(dynamic = true)
    private List<String> args;

    @Override
    public Bash.Output run(RunContext runContext) throws Exception {
        if (!inputFiles.containsKey("main.js")) {
            throw new Exception("Invalid input files structure, expecting inputFiles property to contain at least a main.js key with javascript code value.");
        }

        this.inputFiles.put("kestra.js", IOUtils.toString(
            Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream("scripts/kestra.js")),
            Charsets.UTF_8
        ));

        return run(runContext, throwFunction((additionalVars) -> {
            Path workingDirectory = this.tmpWorkingDirectory(additionalVars);

            // final command
            List<String> renderer = new ArrayList<>();

            if (this.exitOnFailed) {
                renderer.add("set -o errexit");
            }

            String args = getArgs() == null ? "" : " " + runContext.render(String.join(" ", getArgs()));

            String npmInstall = inputFiles.containsKey("package.json") ? npmPath + " i > /dev/null" : "";

            renderer.addAll(Arrays.asList(
                "PATH=\"$PATH:" + new File(nodePath).getParent() + "\"",
                "cd " + workingDirectory,
                npmInstall,
                nodePath + " main.js" + args
            ));

            return String.join("\n", renderer);
        }));
    }
}
