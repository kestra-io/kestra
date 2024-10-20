package io.kestra.plugin.core.execution;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.utils.Await;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static io.kestra.core.utils.Rethrow.throwRunnable;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class ResumeTest extends AbstractMemoryRunnerTest {
    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Test
    void resume() throws Exception {
        Execution pause = runnerUtils.runOneUntilPaused(null, "io.kestra.tests", "pause");
        String pauseId = pause.getId();

        Execution resume = runnerUtils.runOne(null, "io.kestra.tests", "resume-execution", null, (flow, execution) -> Map.of("executionId", pauseId));
        assertThat(resume.getState().getCurrent(), is(State.Type.SUCCESS));

        Await.until(
            () -> executionRepository.findById(null, pauseId).orElseThrow().getState().getCurrent().isTerminated(),
            Duration.ofMillis(100),
            Duration.ofSeconds(5)
        );
    }
}