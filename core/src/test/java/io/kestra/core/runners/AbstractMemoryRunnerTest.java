package io.kestra.core.runners;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.runner.memory.MemoryRunner;
import org.junit.jupiter.api.BeforeEach;

import jakarta.inject.Inject;
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
            TestsUtils.loads(repositoryLoader);
            runner.run();
        }
    }
}
