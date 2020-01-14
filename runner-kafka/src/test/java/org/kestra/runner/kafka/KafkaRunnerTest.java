package org.kestra.runner.kafka;

import com.google.common.collect.ImmutableMap;
import io.micronaut.test.annotation.MicronautTest;
import org.kestra.core.runners.ListenersTest;
import org.kestra.core.utils.TestsUtils;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.repositories.LocalFlowRepositoryLoader;
import org.kestra.core.runners.RunnerUtils;
import org.kestra.core.runners.StandAloneRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

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
        TestsUtils.loads(repositoryLoader);
    }

    @Test
    void full() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "full", null, null, Duration.ofSeconds(15));

        assertThat(execution.getTaskRunList(), hasSize(13));
    }

    @Test
    void errors() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "errors", null, null, Duration.ofSeconds(15));

        assertThat(execution.getTaskRunList(), hasSize(7));
    }

    @Test
    void sequential() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "sequential");

        assertThat(execution.getTaskRunList(), hasSize(11));
    }

    @Test
    void listeners() throws TimeoutException, IOException, URISyntaxException {
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests")));

        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "listeners",
            null,
            (f, e) -> ImmutableMap.of("string", "OK")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("ok"));
        assertThat(execution.getTaskRunList().size(), is(3));
        assertThat(execution.getTaskRunList().get(2).getTaskId(), is("execution-success-listener"));
    }
}