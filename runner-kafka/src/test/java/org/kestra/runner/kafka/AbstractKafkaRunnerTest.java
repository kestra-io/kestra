package org.kestra.runner.kafka;

import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.BeforeEach;
import org.kestra.core.repositories.LocalFlowRepositoryLoader;
import org.kestra.core.runners.RunnerUtils;
import org.kestra.core.runners.StandAloneRunner;
import org.kestra.core.utils.TestsUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import javax.inject.Inject;

@MicronautTest
public abstract class AbstractKafkaRunnerTest {
    @Inject
    private StandAloneRunner runner;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    private void init() throws IOException, URISyntaxException {
        TestsUtils.loads(repositoryLoader);
        runner.run();
    }
}
