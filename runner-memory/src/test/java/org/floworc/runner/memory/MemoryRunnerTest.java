package org.floworc.runner.memory;

import io.micronaut.test.annotation.MicronautTest;
import org.floworc.core.Utils;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.repositories.LocalFlowRepositoryLoader;
import org.floworc.core.runners.RunnerUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

@MicronautTest
class MemoryRunnerTest {
    @Inject
    private MemoryRunner runner;

    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    private LocalFlowRepositoryLoader repositoryLoader;

    @BeforeEach
    private void init() throws IOException, URISyntaxException {
        if (!runner.isRunning()) {
            runner.run();
            Utils.loads(repositoryLoader);
        }
    }

    @Test
    void full() throws TimeoutException {
        Execution execution = runnerUtils.runOne("full");

        assertThat(execution.getTaskRunList(), hasSize(13));
    }

    @Test
    void errors() throws TimeoutException {
        Execution execution = runnerUtils.runOne("errors");

        assertThat(execution.getTaskRunList(), hasSize(7));
    }
}