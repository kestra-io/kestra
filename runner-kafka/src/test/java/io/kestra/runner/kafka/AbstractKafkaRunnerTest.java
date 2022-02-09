package io.kestra.runner.kafka;

import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.runners.StandAloneRunner;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

@MicronautTest
public abstract class AbstractKafkaRunnerTest {
    @Inject
    private StandAloneRunner runner;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @Inject
    protected KafkaTemplateExecutor kafkaTemplateExecutor;

    @BeforeEach
    private void init() throws IOException, URISyntaxException {
        kafkaTemplateExecutor.setTemplates(List.of());
        TestsUtils.loads(repositoryLoader);
        runner.setSchedulerEnabled(false);
        runner.run();
    }
}
