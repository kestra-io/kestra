package io.kestra.plugin.core.execution;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.utils.Await;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class ResumeTest {

    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Test
    @LoadFlows({"flows/valids/pause.yaml",
        "flows/valids/resume-execution.yaml"})
    void resume() throws Exception {
        Execution pause = runnerUtils.runOneUntilPaused(null, "io.kestra.tests", "pause");
        String pauseId = pause.getId();

        Execution resume = runnerUtils.runOne(null, "io.kestra.tests", "resume-execution", null, (flow, execution) -> Map.of("executionId", pauseId));
        assertThat(resume.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Await.until(
            () -> executionRepository.findById(null, pauseId).orElseThrow().getState().getCurrent().isTerminated(),
            Duration.ofMillis(100),
            Duration.ofSeconds(5)
        );
    }
}