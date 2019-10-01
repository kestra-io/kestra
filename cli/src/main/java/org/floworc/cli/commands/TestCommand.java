package org.floworc.cli.commands;

import lombok.extern.slf4j.Slf4j;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.core.repositories.LocalFlowRepositoryLoader;
import org.floworc.core.runners.RunnerUtils;
import org.floworc.runner.memory.MemoryRunner;
import picocli.CommandLine;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;

@CommandLine.Command(
    name = "test",
    description = "test a flow"
)
@Slf4j
public class TestCommand implements Runnable {
    @CommandLine.Parameters(description = "the flow file to test")
    private Path file;

    @Inject
    private MemoryRunner runner;

    @Inject
    private LocalFlowRepositoryLoader repositoryLoader;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private RunnerUtils runnerUtils;

    public void run() {
        try {
            runner.run();
            repositoryLoader.load(file.toFile());

            List<Flow> all = flowRepository.findAll();
            if (all.size() != 1) {
                throw new IllegalArgumentException("Too many flow found, need 1, found " + all.size());
            }

            runnerUtils.runOne(all.get(0).getId());
            runner.close();
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException(e);
        }

    }
}