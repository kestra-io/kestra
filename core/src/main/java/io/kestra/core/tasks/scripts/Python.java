package io.kestra.core.tasks.scripts;

import com.google.common.base.Charsets;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;

import java.nio.file.Path;
import java.util.*;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import static io.kestra.core.utils.Rethrow.throwFunction;
import static io.kestra.core.utils.Rethrow.throwSupplier;

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
public class Python extends AbstractPython implements RunnableTask<ScriptOutput> {
    @Schema(
        title = "The commands to run",
        description = "Default command will be launched with `./bin/python main.py {{args}}`"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    @Builder.Default
    protected List<String> commands = Collections.singletonList("./bin/python main.py");

    @Schema(
        title = "Python command args",
        description = "Arguments list to pass to main python script"
    )
    @PluginProperty(dynamic = true)
    private List<String> args;

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        if (!inputFiles.containsKey("main.py") && this.commands.size() == 1 && this.commands.get(0).equals("./bin/python main.py")) {
            throw new Exception("Invalid input files structure, expecting inputFiles property to contain at least a main.py key with python code value.");
        }

        this.inputFiles.put("kestra.py", IOUtils.toString(
            Objects.requireNonNull(AbstractPython.class.getClassLoader().getResourceAsStream("scripts/kestra.py")),
            Charsets.UTF_8
        ));

        return run(runContext, throwSupplier(() -> {
            List<String> renderer = new ArrayList<>();
            renderer.add(this.virtualEnvCommand(runContext, requirements));

            for (String command : commands) {
                String argsString = args == null ? "" : " " + runContext.render(String.join(" ", args), additionalVars);

                renderer.add(runContext.render(command, additionalVars) + argsString);
            }

            return String.join("\n", renderer);
        }));
    }
}
