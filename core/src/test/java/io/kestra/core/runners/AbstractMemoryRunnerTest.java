package io.kestra.core.runners;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import org.junit.jupiter.api.BeforeEach;

import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;

@KestraTest
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
