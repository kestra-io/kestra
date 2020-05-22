package org.kestra.core.runners;

import io.micronaut.test.annotation.MicronautTest;
import org.kestra.core.utils.TestsUtils;
import org.kestra.core.repositories.LocalFlowRepositoryLoader;
import org.kestra.runner.memory.MemoryRunner;
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
    protected void init() throws IOException, URISyntaxException {
        if (!runner.isRunning()) {
            runner.run();
            TestsUtils.loads(repositoryLoader);
        }
    }
}
