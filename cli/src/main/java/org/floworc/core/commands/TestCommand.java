package org.floworc.core.commands;

import lombok.extern.slf4j.Slf4j;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.serializers.YamlFlowParser;
import org.floworc.runner.memory.MemoryRunner;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;

@CommandLine.Command(
    name = "test",
    description = "test a flow"
)
@Slf4j
public class TestCommand implements Runnable {
    @CommandLine.Parameters(description = "the flow file to test")
    private File file;

    private static final YamlFlowParser yamlFlowParser = new YamlFlowParser();

    public void run() {
        MemoryRunner runner = new MemoryRunner();

        Flow flow = null;
        try {
            flow = yamlFlowParser.parse(file);
            Execution execution = runner.run(flow);
        } catch (IOException | InterruptedException e) {
            log.error("Failed flow", e);
        }
    }
}