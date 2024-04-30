package io.kestra.core.tasks.flows;

import com.google.common.io.CharStreams;
import io.kestra.core.exceptions.MissingRequiredArgument;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.runners.TestStreamingFileUpload;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tasks.outputs.OutputValues;
import io.micronaut.http.MediaType;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;
import reactor.core.publisher.Flux;

import java.io.InputStreamReader;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PauseTest extends AbstractMemoryRunnerTest {
    @Inject
    Suite suite;

    @Test
    void run() throws Exception {
        suite.run(runnerUtils);
    }

    @Disabled("This test is too flaky and it always pass in JDBC and Kafka")
    void delay() throws Exception {
        suite.runDelay(runnerUtils);
    }

    @Disabled("This test is too flaky and it always pass in JDBC and Kafka")
    void parallelDelay() throws Exception {
        suite.runParallelDelay(runnerUtils);
    }

    @Test
    void timeout() throws Exception {
        suite.runTimeout(runnerUtils);
    }

    @Test
    void runEmptyTasks() throws Exception {
        suite.runEmptyTasks(runnerUtils);
    }

    @Test
    void runOnResume() throws Exception {
        suite.runOnResume(runnerUtils);
    }

    @Test
    void runOnResumeMissingInputs() throws Exception {
        suite.runOnResumeMissingInputs(runnerUtils);
    }

    @Test
    void runOnResumeOptionalInputs() throws Exception {
        suite.runOnResumeOptionalInputs(runnerUtils);
    }

    @Singleton
    public static class Suite {
        @Inject
        ExecutionService executionService;

        @Inject
        FlowRepositoryInterface flowRepository;

        @Inject
        StorageInterface storageInterface;

        @Inject
        @Named(QueueFactoryInterface.EXECUTION_NAMED)
        protected QueueInterface<Execution> executionQueue;

        public void run(RunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused(null, "io.kestra.tests", "pause", null, null, Duration.ofSeconds(30));
            String executionId = execution.getId();
            Flow flow = flowRepository.findByExecution(execution);

            assertThat(execution.getState().getCurrent(), is(State.Type.PAUSED));
            assertThat(execution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.PAUSED));
            assertThat(execution.getTaskRunList(), hasSize(1));

            Execution restarted = executionService.markAs(
                execution,
                flow,
                execution.findTaskRunByTaskIdAndValue("pause", List.of()).getId(),
                State.Type.RUNNING
            );

            execution = runnerUtils.awaitExecution(
                e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
                () -> executionQueue.emit(restarted),
                Duration.ofSeconds(5)
            );

            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        }

        public void runDelay(RunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused(null, "io.kestra.tests", "pause-delay", null, null, Duration.ofSeconds(30));
            String executionId = execution.getId();

            assertThat(execution.getState().getCurrent(), is(State.Type.PAUSED));
            assertThat(execution.getTaskRunList(), hasSize(1));

            execution = runnerUtils.awaitExecution(
                e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
                () -> {},
                Duration.ofSeconds(5)
            );

            assertThat(execution.getTaskRunList().get(0).getState().getHistories().stream().filter(history -> history.getState() == State.Type.PAUSED).count(), is(1L));
            assertThat(execution.getTaskRunList().get(0).getState().getHistories().stream().filter(history -> history.getState() == State.Type.RUNNING).count(), is(2L));
            assertThat(execution.getTaskRunList(), hasSize(3));
        }

        public void runParallelDelay(RunnerUtils runnerUtils) throws TimeoutException {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "each-parallel-pause", Duration.ofSeconds(30));

            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
            assertThat(execution.getTaskRunList(), hasSize(7));
        }

        public void runTimeout(RunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused(null, "io.kestra.tests", "pause-timeout", null, null, Duration.ofSeconds(30));
            String executionId = execution.getId();

            assertThat(execution.getState().getCurrent(), is(State.Type.PAUSED));
            assertThat(execution.getTaskRunList(), hasSize(1));

            execution = runnerUtils.awaitExecution(
                e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.FAILED,
                () -> {},
                Duration.ofSeconds(5)
            );

            assertThat("Task runs were: " + execution.getTaskRunList().toString(), execution.getTaskRunList().get(0).getState().getHistories().stream().filter(history -> history.getState() == State.Type.PAUSED).count(), is(1L));
            assertThat(execution.getTaskRunList().get(0).getState().getHistories().stream().filter(history -> history.getState() == State.Type.RUNNING).count(), is(1L));
            assertThat(execution.getTaskRunList().get(0).getState().getHistories().stream().filter(history -> history.getState() == State.Type.FAILED).count(), is(1L));
            assertThat(execution.getTaskRunList(), hasSize(1));
        }

        public void runEmptyTasks(RunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused(null, "io.kestra.tests", "pause_no_tasks", null, null, Duration.ofSeconds(30));
            String executionId = execution.getId();
            Flow flow = flowRepository.findByExecution(execution);

            assertThat(execution.getState().getCurrent(), is(State.Type.PAUSED));
            assertThat(execution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.PAUSED));
            assertThat(execution.getTaskRunList(), hasSize(1));

            Execution restarted = executionService.markAs(
                execution,
                flow,
                execution.findTaskRunByTaskIdAndValue("pause", List.of()).getId(),
                State.Type.RUNNING
            );

            execution = runnerUtils.awaitExecution(
                e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
                () -> executionQueue.emit(restarted),
                Duration.ofSeconds(10)
            );

            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        }

        @SuppressWarnings("unchecked")
        public void runOnResume(RunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused(null, "io.kestra.tests", "pause_on_resume", null, null, Duration.ofSeconds(30));
            String executionId = execution.getId();
            Flow flow = flowRepository.findByExecution(execution);

            assertThat(execution.getState().getCurrent(), is(State.Type.PAUSED));
            assertThat(execution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.PAUSED));
            assertThat(execution.getTaskRunList(), hasSize(1));

            Execution restarted = executionService.resume(
                execution,
                flow,
                State.Type.RUNNING,
                Map.of("asked", "restarted"),
                Flux.just(new TestStreamingFileUpload("data", executionId.getBytes(), MediaType.TEXT_PLAIN_TYPE))
            );

            execution = runnerUtils.awaitExecution(
                e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
                () -> executionQueue.emit(restarted),
                Duration.ofSeconds(10)
            );

            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

            Map<String, Object> outputs = (Map<String, Object>) execution.findTaskRunsByTaskId("last").get(0).getOutputs().get("values");
            assertThat(outputs.get("asked"), is("restarted"));
            assertThat((String) outputs.get("data"), startsWith("kestra://"));
            assertThat(
                CharStreams.toString(new InputStreamReader(storageInterface.get(null, URI.create((String) outputs.get("data"))))),
                is(executionId)
            );
        }

        public void runOnResumeMissingInputs(RunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused(null, "io.kestra.tests", "pause_on_resume", null, null, Duration.ofSeconds(30));
            Flow flow = flowRepository.findByExecution(execution);

            assertThat(execution.getState().getCurrent(), is(State.Type.PAUSED));

            MissingRequiredArgument e = assertThrows(
                MissingRequiredArgument.class,
                () -> executionService.resume(execution, flow, State.Type.RUNNING)
            );

            assertThat(e.getMessage(), containsString("required input value 'asked'"));
        }

        @SuppressWarnings("unchecked")
        public void runOnResumeOptionalInputs(RunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused(null, "io.kestra.tests", "pause_on_resume_optional", null, null, Duration.ofSeconds(30));
            String executionId = execution.getId();
            Flow flow = flowRepository.findByExecution(execution);

            assertThat(execution.getState().getCurrent(), is(State.Type.PAUSED));

            Execution restarted = executionService.resume(execution, flow, State.Type.RUNNING);

            execution = runnerUtils.awaitExecution(
                e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
                () -> executionQueue.emit(restarted),
                Duration.ofSeconds(10)
            );

            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

            Map<String, Object> outputs = (Map<String, Object>) execution.findTaskRunsByTaskId("last").get(0).getOutputs().get("values");
            assertThat(outputs.get("asked"), is("MISSING"));
        }
    }
}