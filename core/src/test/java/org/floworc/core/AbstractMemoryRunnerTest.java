package org.floworc.core;

import io.micronaut.test.annotation.MicronautTest;
import org.floworc.core.repositories.LocalFlowRepositoryLoader;
import org.floworc.core.runners.RunnerUtils;
import org.floworc.runner.memory.MemoryRunner;
import org.junit.jupiter.api.BeforeEach;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;

@MicronautTest
abstract public class AbstractMemoryRunnerTest {
    @Inject
    protected MemoryRunner runner;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    private void init() throws IOException, URISyntaxException {
        if (!runner.isRunning()) {
            runner.run();
            Utils.loads(repositoryLoader);
        }
    }
}
