package io.kestra.core.runners;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import org.junit.jupiter.api.BeforeEach;

import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;

@MicronautTest(transactional = false)
abstract public class AbstractMemoryRunnerTest {
    @Inject
    protected StandAloneRunner runner;

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
