package io.kestra.plugin.core.flow;

import com.google.common.io.CharStreams;
import io.kestra.core.exceptions.InputOutputValidationException;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.MicronautHttpData;
import io.micronaut.http.server.netty.multipart.NettyCompletedAttribute;
import io.micronaut.http.server.netty.multipart.NettyCompletedFileUpload;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.multipart.*;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest(startRunner = true)
public class PauseTest {

    @Inject
    TestRunnerUtils runnerUtils;
    @Inject
    Suite suite;

    @Test
    @LoadFlows({"flows/valids/pause-test.yaml"})
    void run() throws Exception {
        suite.run(runnerUtils);
    }

    @FlakyTest(description = "This test is too flaky and it always pass in JDBC and Kafka")
    @Test
    @LoadFlows("flows/valids/pause-delay.yaml")
    void delay() throws Exception {
        suite.runDelay(runnerUtils);
    }

    @FlakyTest(description = "This test is too flaky and it always pass in JDBC and Kafka")
    @Test
    @LoadFlows("flows/valids/pause-duration-from-input.yaml")
    void delayFromInput() throws Exception {
        suite.runDurationFromInput(runnerUtils);
    }

    @FlakyTest(description = "This test is too flaky and it always pass in JDBC and Kafka")
    @Test
    @LoadFlows("flows/valids/each-parallel-pause.yml")
    void parallelDelay() throws Exception {
        suite.runParallelDelay(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/pause-timeout.yaml"})
    void timeout() throws Exception {
        suite.runTimeout(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/pause-timeout-allow-failure.yaml"})
    void timeoutAllowFailure() throws Exception {
        suite.runTimeoutAllowFailure(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/pause_no_tasks.yaml"})
    void runEmptyTasks() throws Exception {
        suite.runEmptyTasks(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/pause_on_resume.yaml"})
    void runOnResume() throws Exception {
        suite.runOnResume(runnerUtils);
    }

    @Test
    @LoadFlows(value = {"flows/valids/pause_on_resume.yaml"}, tenantId = "tenant1")
    void runOnResumeMissingInputs() throws Exception {
        suite.runOnResumeMissingInputs("tenant1", runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/pause_on_resume_optional.yaml"})
    void runOnResumeOptionalInputs() throws Exception {
        suite.runOnResumeOptionalInputs(runnerUtils);
    }

    @Test
    @LoadFlows(value = {"flows/valids/pause-behavior.yaml"}, tenantId = "resume")
    void runDurationWithCONTINUEBehavior() throws Exception {
        suite.runDurationWithBehavior("resume", runnerUtils, Pause.Behavior.RESUME);
    }

    @Test
    @LoadFlows(value = {"flows/valids/pause-behavior.yaml"}, tenantId = "fail")
    void runDurationWithFAILBehavior() throws Exception {
        suite.runDurationWithBehavior("fail", runnerUtils, Pause.Behavior.FAIL);
    }

    @Test
    @LoadFlows(value = {"flows/valids/pause-behavior.yaml"}, tenantId = "warn")
    void runDurationWithWARNBehavior() throws Exception {
        suite.runDurationWithBehavior("warn", runnerUtils, Pause.Behavior.WARN);
    }

    @Test
    @LoadFlows(value = {"flows/valids/pause-behavior.yaml"}, tenantId = "cancel")
    void runDurationWithCANCELBehavior() throws Exception {
        suite.runDurationWithBehavior("cancel", runnerUtils, Pause.Behavior.CANCEL);
    }

    @Test
    @ExecuteFlow("flows/valids/pause_on_pause.yaml")
    void shouldExecuteOnPauseTask(Execution execution) throws Exception {
        suite.shouldExecuteOnPauseTask(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/pause-errors-finally-after-execution.yaml")
    void shouldExecuteErrorsFinallyAndAfterExecution(Execution execution) throws Exception {
        suite.shouldExecuteErrorsFinallyAndAfterExecution(execution);
    }

    @Singleton
    public static class Suite {
        @Inject
        ExecutionService executionService;

        @Inject
        FlowRepositoryInterface flowRepository;

        @Inject
        StorageInterface storageInterface;

        public void run(TestRunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "pause-test", null, null, Duration.ofSeconds(30));
            String executionId = execution.getId();
            Flow flow = flowRepository.findByExecution(execution);

            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.PAUSED);
            assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.PAUSED);
            assertThat(execution.getTaskRunList()).hasSize(1);

            Execution restarted = executionService.markAs(
                execution,
                flow,
                execution.findTaskRunByTaskIdAndValue("pause", List.of()).getId(),
                State.Type.RUNNING
            );

            execution = runnerUtils.emitAndAwaitExecution(
                e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
                restarted
            );

            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        }

        public void runDelay(TestRunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "pause-delay", null, null, Duration.ofSeconds(30));
            String executionId = execution.getId();

            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.PAUSED);
            assertThat(execution.getTaskRunList()).hasSize(1);

            execution = runnerUtils.awaitExecution(
                e ->
                    e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
                execution
            );

            assertThat(execution.getTaskRunList().getFirst().getState().getHistories().stream().filter(history -> history.getState() == State.Type.PAUSED).count()).isEqualTo(1L);
            assertThat(execution.getTaskRunList().getFirst().getState().getHistories().stream().filter(history -> history.getState() == State.Type.RUNNING).count()).isEqualTo(2L);
            assertThat(execution.getTaskRunList()).hasSize(3);
        }

        public void runDurationFromInput(TestRunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "pause-duration-from-input", null, null, Duration.ofSeconds(30));
            String executionId = execution.getId();

            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.PAUSED);
            assertThat(execution.getTaskRunList()).hasSize(1);

            execution = runnerUtils.awaitExecution(
                e ->
                    e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
                execution
            );

            assertThat(execution.getTaskRunList().getFirst().getState().getHistories().stream().filter(history -> history.getState() == State.Type.PAUSED).count()).isEqualTo(1L);
            assertThat(execution.getTaskRunList().getFirst().getState().getHistories().stream().filter(history -> history.getState() == State.Type.RUNNING).count()).isEqualTo(2L);
            assertThat(execution.getTaskRunList()).hasSize(3);
        }

        public void runParallelDelay(TestRunnerUtils runnerUtils) throws TimeoutException, QueueException {
            Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "each-parallel-pause", Duration.ofSeconds(30));

            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
            assertThat(execution.getTaskRunList()).hasSize(7);
        }

        public void runTimeout(TestRunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "pause-timeout", null, null, Duration.ofSeconds(30));
            String executionId = execution.getId();

            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.PAUSED);
            assertThat(execution.getTaskRunList()).hasSize(1);

            execution = runnerUtils.awaitExecution(
                e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.FAILED,
                execution
            );

            assertThat(execution.getTaskRunList().getFirst().getState().getHistories().stream().filter(history -> history.getState() == State.Type.PAUSED).count()).as("Task runs were: " + execution.getTaskRunList().toString()).isEqualTo(1L);
            assertThat(execution.getTaskRunList().getFirst().getState().getHistories().stream().filter(history -> history.getState() == State.Type.RUNNING).count()).isEqualTo(2L);
            assertThat(execution.getTaskRunList().getFirst().getState().getHistories().stream().filter(history -> history.getState() == State.Type.FAILED).count()).isEqualTo(1L);
            assertThat(execution.getTaskRunList()).hasSize(1);
        }

        public void runTimeoutAllowFailure(TestRunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "pause-timeout-allow-failure", null, null, Duration.ofSeconds(30));
            String executionId = execution.getId();

            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.PAUSED);
            assertThat(execution.getTaskRunList()).hasSize(1);

            execution = runnerUtils.awaitExecution(
                e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.WARNING,
                execution
            );

            assertThat(execution.getTaskRunList().getFirst().getState().getHistories().stream().filter(history -> history.getState() == State.Type.PAUSED).count()).as("Task runs were: " + execution.getTaskRunList().toString()).isEqualTo(1L);
            assertThat(execution.getTaskRunList().getFirst().getState().getHistories().stream().filter(history -> history.getState() == State.Type.RUNNING).count()).isEqualTo(2L);
            assertThat(execution.getTaskRunList().getFirst().getState().getHistories().stream().filter(history -> history.getState() == State.Type.WARNING).count()).isEqualTo(1L);
            assertThat(execution.getTaskRunList()).hasSize(3);
        }

        public void runEmptyTasks(TestRunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "pause_no_tasks", null, null, Duration.ofSeconds(30));
            String executionId = execution.getId();
            Flow flow = flowRepository.findByExecution(execution);

            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.PAUSED);
            assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.PAUSED);
            assertThat(execution.getTaskRunList()).hasSize(1);

            Execution restarted = executionService.markAs(
                execution,
                flow,
                execution.findTaskRunByTaskIdAndValue("pause", List.of()).getId(),
                State.Type.RUNNING
            );

            execution = runnerUtils.emitAndAwaitExecution(
                e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
                restarted
            );

            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        }

        @SuppressWarnings("unchecked")
        public void runOnResume(TestRunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "pause_on_resume", null, null, Duration.ofSeconds(30));
            String executionId = execution.getId();
            Flow flow = flowRepository.findByExecution(execution);

            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.PAUSED);
            assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.PAUSED);
            assertThat(execution.getTaskRunList()).hasSize(1);

            CompletedPart part1 = new NettyCompletedAttribute(new MemoryAttribute("asked", "restarted"));
            byte[] data = executionId.getBytes();
            HttpDataFactory httpDataFactory = new MicronautHttpData.Factory(new HttpServerConfiguration.MultipartConfiguration(), null);
            FileUpload fileUpload = httpDataFactory.createFileUpload(null, "files", "data", MediaType.TEXT_PLAIN, null, Charset.defaultCharset(), data.length);
            fileUpload.addContent(Unpooled.copiedBuffer(data), true);
            CompletedPart part2 = new NettyCompletedFileUpload(fileUpload);
            Execution restarted = executionService.resume(
                execution,
                flow,
                State.Type.RUNNING,
                Flux.just(part1, part2),
                null
            ).block();

            execution = runnerUtils.emitAndAwaitExecution(
                e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
                restarted
            );

            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

            Map<String, Object> outputs = (Map<String, Object>) execution.findTaskRunsByTaskId("last").getFirst().getOutputs().get("values");
            assertThat(outputs.get("asked")).isEqualTo("restarted");
            assertThat((String) outputs.get("data")).startsWith("kestra://");
            assertThat(CharStreams.toString(new InputStreamReader(storageInterface.get(MAIN_TENANT, null, URI.create((String) outputs.get("data")))))).isEqualTo(executionId);
        }

        public void runOnResumeMissingInputs(String tenantId, TestRunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused(tenantId, "io.kestra.tests", "pause_on_resume", null, null, Duration.ofSeconds(30));
            Flow flow = flowRepository.findByExecution(execution);

            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.PAUSED);

            InputOutputValidationException e = assertThrows(
                InputOutputValidationException.class,
                () -> executionService.resume(execution, flow, State.Type.RUNNING, Mono.empty(), Pause.Resumed.now()).block()
            );

            assertThat(e.getMessage()).contains(  "Missing required input:asked");
        }

        @SuppressWarnings("unchecked")
        public void runOnResumeOptionalInputs(TestRunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "pause_on_resume_optional", null, null, Duration.ofSeconds(30));
            String executionId = execution.getId();
            Flow flow = flowRepository.findByExecution(execution);

            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.PAUSED);

            Execution restarted = executionService.resume(execution, flow, State.Type.RUNNING, Pause.Resumed.now());

            execution = runnerUtils.emitAndAwaitExecution(
                e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
                restarted
            );

            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

            Map<String, Object> outputs = (Map<String, Object>) execution.findTaskRunsByTaskId("last").getFirst().getOutputs().get("values");
            assertThat(outputs.get("asked")).isEqualTo("MISSING");
        }

        public void runDurationWithBehavior(String tenantId, TestRunnerUtils runnerUtils, Pause.Behavior behavior) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused(tenantId, "io.kestra.tests", "pause-behavior", null, (unused, _unused) -> Map.of("behavior", behavior), Duration.ofSeconds(30));
            String executionId = execution.getId();

            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.PAUSED);
            assertThat(execution.getTaskRunList()).hasSize(1);

            execution = runnerUtils.awaitExecution(
                e -> e.getId().equals(executionId) && e.getState().getCurrent().isTerminated(),
                execution
            );

            State.Type finalState = behavior == Pause.Behavior.RESUME ? State.Type.SUCCESS : behavior.mapToState();
            boolean terminateAfterPause = behavior == Pause.Behavior.CANCEL || behavior == Pause.Behavior.FAIL;
            assertThat(execution.getTaskRunList()).hasSize(terminateAfterPause ? 1 : 2);
            assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(finalState);
            assertThat(execution.getState().getCurrent()).isEqualTo(finalState);
        }

        public void shouldExecuteOnPauseTask(Execution execution) throws Exception {
            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
            assertThat(execution.getTaskRunList()).hasSize(2);
            assertThat(execution.getTaskRunList().getLast().getTaskId()).isEqualTo("hello");
            assertThat(execution.getTaskRunList().getLast().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        }

        public void shouldExecuteErrorsFinallyAndAfterExecution(Execution execution) throws Exception {
            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
            assertThat(execution.getTaskRunList()).hasSize(4);
            assertThat(execution.findTaskRunsByTaskId("pause")).hasSize(1);
            assertThat(execution.findTaskRunsByTaskId("pause").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
            assertThat(execution.findTaskRunsByTaskId("logError")).hasSize(1);
            assertThat(execution.findTaskRunsByTaskId("logError").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
            assertThat(execution.findTaskRunsByTaskId("logFinally")).hasSize(1);
            assertThat(execution.findTaskRunsByTaskId("logFinally").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
            assertThat(execution.findTaskRunsByTaskId("logAfter")).hasSize(1);
            assertThat(execution.findTaskRunsByTaskId("logAfter").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        }
    }
}