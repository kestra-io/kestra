package org.floworc.task.notifications.slack;

import org.floworc.core.Utils;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.runners.AbstractMemoryRunnerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class SlackExecutionTest extends AbstractMemoryRunnerTest {
    @BeforeEach
    private void initLocal() throws IOException, URISyntaxException {
        Utils.loads(repositoryLoader, SlackIncomingWebhookTest.class.getClassLoader().getResource("flows"));
    }

    @Test
    void flow() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.floworc.tests", "slack");

        assertThat(execution.getTaskRunList(), hasSize(2));
    }
}