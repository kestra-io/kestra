package org.floworc.runner.kafka;

import io.micronaut.test.annotation.MicronautTest;
import org.floworc.core.Utils;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.repositories.LocalFlowRepositoryLoader;
import org.floworc.core.runners.RunnerUtils;
import org.floworc.core.runners.StandAloneRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

@MicronautTest
class KafkaRunnerTest {
    @Inject
    private StandAloneRunner runner;

    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    private LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    private void init() throws IOException, URISyntaxException {
        runner.setThreads(1);
        runner.run();
        Utils.loads(repositoryLoader);
    }

    @Test
    void full() throws TimeoutException {
        Execution execution = runnerUtils.runOne("full", null, Duration.ofSeconds(15));

        assertThat(execution.getTaskRunList(), hasSize(13));
    }

    @Test
    void errors() throws TimeoutException {
        Execution execution = runnerUtils.runOne("errors", null, Duration.ofSeconds(15));

        assertThat(execution.getTaskRunList(), hasSize(7));
    }
}