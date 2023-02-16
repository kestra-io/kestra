package io.kestra.core.tasks.scripts;

import com.google.common.base.Charsets;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
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
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static io.kestra.core.utils.Rethrow.throwFunction;
import static io.kestra.core.utils.Rethrow.throwSupplier;

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
                "    axios.get('http://google.fr').then(d => { console.log(d.status); Kestra.outputs({'status': d.status, 'text': d.data})})",
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
                "          \"test\": \"echo `Error: no test specified` && exit 1\"",
                "      },",
                "      \"author\": \"\",",
                "      \"license\": \"ISC\"",
                "    }",
                "args:",
                "  - data.json",
            }
        ),
        @Example(
            title = "Execute a node script with an input file from Kestra's local storage created by a previous task.",
            code = {
                "inputFiles:",
                "  data.csv: {{outputs.previousTaskId.uri}}",
                "  main.js: |",
                "    const fs = require('fs')",
                "    const result = fs.readFileSync('data.csv', 'utf-8')",
                "    console.log(result)"
            }
        )
    }
)
public class Node extends AbstractBash implements RunnableTask<ScriptOutput> {
    @Builder.Default
    @Schema(
        title = "The node interpreter to use",
        description = "Set the node interpreter path to use"
    )
    @PluginProperty
    private final String nodePath = "node";

    @Builder.Default
    @Schema(
        title = "The npm binary to use",
        description = "Set the npm binary path for node dependencies setup"
    )
    @PluginProperty
    private final String npmPath = "npm";

    @Schema(
        title = "node command args",
        description = "Arguments list to pass to main javascript script"

    )
    @PluginProperty(dynamic = true)
    private List<String> args;

    @Override
    protected Map<String, String> finalInputFiles(RunContext runContext) throws IOException, IllegalVariableEvaluationException {
        Map<String, String> map = super.finalInputFiles(runContext);

        map.put("kestra.js", IOUtils.toString(
            Objects.requireNonNull(Node.class.getClassLoader().getResourceAsStream("scripts/kestra.js")),
            Charsets.UTF_8
        ));

        return map;
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        Map<String, String> finalInputFiles = this.finalInputFiles(runContext);

        if (!finalInputFiles.containsKey("main.js")) {
            throw new Exception("Invalid input files structure, expecting inputFiles property to contain at least a main.js key with javascript code value.");
        }

        return run(runContext, throwSupplier(() -> {
            // final command
            List<String> renderer = new ArrayList<>();

            if (this.exitOnFailed) {
                renderer.add("set -o errexit");
            }

            String args = getArgs() == null ? "" : " " + runContext.render(String.join(" ", getArgs()));

            String npmInstall = finalInputFiles.containsKey("package.json") ? npmPath + " i > /dev/null" : "";

            renderer.addAll(Arrays.asList(
                "PATH=\"$PATH:" + new File(nodePath).getParent() + "\"",
                npmInstall,
                nodePath + " main.js" + args
            ));

            return String.join("\n", renderer);
        }));
    }
}
