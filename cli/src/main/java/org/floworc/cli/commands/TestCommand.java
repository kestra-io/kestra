package org.floworc.cli.commands;

import lombok.extern.slf4j.Slf4j;
import org.floworc.cli.AbstractCommand;
import org.floworc.core.exceptions.MissingRequiredInput;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.core.repositories.LocalFlowRepositoryLoader;
import org.floworc.core.runners.RunnerUtils;
import org.floworc.runner.memory.MemoryRunner;
import picocli.CommandLine;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@CommandLine.Command(
    name = "test",
    description = "test a flow"
)
@Slf4j
public class TestCommand extends AbstractCommand {
    @CommandLine.Parameters(index = "0", description = "the flow file to test")
    private Path file;

    @CommandLine.Parameters(index = "1..*", description = "the inputs to pass as key pair value")
    private List<String> inputs = new ArrayList<>();

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    private MemoryRunner runner;

    @Inject
    private LocalFlowRepositoryLoader repositoryLoader;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private RunnerUtils runnerUtils;

    public TestCommand() {
        super(false);
    }

    @Override
    public void run() {
        super.run();

        Map<String, String> inputs = new HashMap<>();

        for (int i = 0; i < this.inputs.size(); i=i+2) {
            if (this.inputs.size() <= i + 1) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), "Invalid key pair value for inputs");
            }

            inputs.put(this.inputs.get(i), this.inputs.get(i+1));
        }

        try {
            runner.run();
            repositoryLoader.load(file.toFile());

            List<Flow> all = flowRepository.findAll();
            if (all.size() != 1) {
                throw new IllegalArgumentException("Too many flow found, need 1, found " + all.size());
            }

            runnerUtils.runOne(
                all.get(0),
                (flow, execution) -> runnerUtils.typedInputs(flow, execution, inputs),
                Duration.ofHours(1)
            );

            runner.close();
        } catch (MissingRequiredInput e) {
            throw new CommandLine.ParameterException(this.spec.commandLine(), e.getMessage());
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }
}