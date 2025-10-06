package io.kestra.core.runners;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.test.flow.TaskFixture;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@KestraTest(startRunner = true)
class TestSuiteTest {

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    protected FlowRepositoryInterface flowRepository;

    @Inject
    protected ApplicationContext applicationContext;

    @Test
    @LoadFlows({"flows/valids/return.yaml"})
    void withoutAnyTaskFixture() throws QueueException, TimeoutException {
        var fixtures = List.<TaskFixture>of();

        var executionResult = runReturnFlow(fixtures, MAIN_TENANT);

        assertThat(executionResult.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertOutputForTask(executionResult, "task-id")
            .isEqualTo("task-id");
        assertOutputForTask(executionResult, "flow-id")
            .isEqualTo("return");
        assertOutputForTask(executionResult, "date")
            .satisfies(output -> {
                assertThat(output).asString().isNotBlank();
                assertThat(ZonedDateTime.parse((String) output)).isCloseTo(ZonedDateTime.now(), within(300, ChronoUnit.SECONDS));
            });
    }

    @Test
    @LoadFlows(value = {"flows/valids/return.yaml"}, tenantId = "tenant1")
    void taskFixture() throws TimeoutException, QueueException {
        var fixtures = List.of(
            TaskFixture.builder()
                .id("date")
                .build()
        );

        var executionResult = runReturnFlow(fixtures, "tenant1");

        assertThat(executionResult.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertOutputForTask(executionResult, "task-id")
            .isEqualTo("task-id");
        assertOutputForTask(executionResult, "flow-id")
            .isEqualTo("return");
        assertOutputForTask(executionResult, "date")
            .isNull();
    }

    @Test
    @LoadFlows(value = {"flows/valids/return.yaml"}, tenantId = "tenant2")
    void twoTaskFixturesOverridingOutput() throws QueueException, TimeoutException {
        var fixtures = List.of(
            TaskFixture.builder()
                .id("date")
                .outputs(Map.of("value", "my-mocked-output-value"))
                .build(),
            TaskFixture.builder()
                .id("flow-id")
                .outputs(Map.of("value", "my-mocked-output-flow-id"))
                .build()
        );

        var executionResult = runReturnFlow(fixtures, "tenant2");

        assertThat(executionResult.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertOutputForTask(executionResult, "task-id")
            .isEqualTo("task-id");
        assertOutputForTask(executionResult, "flow-id")
            .isEqualTo("my-mocked-output-flow-id");
        assertOutputForTask(executionResult, "date")
            .isEqualTo("my-mocked-output-value");
    }

    @Test
    @LoadFlows(value = {"flows/valids/return.yaml"}, tenantId = "tenant3")
    void taskFixturesWithWarningState() throws QueueException, TimeoutException {
        var fixtures = List.of(
            TaskFixture.builder()
                .id("date")
                .state(State.Type.WARNING)
                .build()
        );

        var executionResult = runReturnFlow(fixtures, "tenant3");

        assertThat(executionResult.getState().getCurrent()).isEqualTo(State.Type.WARNING);
        assertTask(executionResult, "task-id")
            .extracting(TaskRun::getState).extracting(State::getCurrent)
            .isEqualTo(State.Type.SUCCESS);
        assertTask(executionResult, "flow-id")
            .extracting(TaskRun::getState).extracting(State::getCurrent)
            .isEqualTo(State.Type.SUCCESS);
        assertTask(executionResult, "date")
            .extracting(TaskRun::getState).extracting(State::getCurrent)
            .isEqualTo(State.Type.WARNING);
    }

    private Execution runReturnFlow(List<TaskFixture> fixtures, String tenantId) throws TimeoutException, QueueException {
        var flow = flowRepository.findById(tenantId, "io.kestra.tests", "return", Optional.empty()).orElseThrow();

        var execution = Execution.builder()
            .id(IdUtils.create())
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .fixtures(fixtures)
            .state(new State())
            .build();

        return runnerUtils.runOne(execution, flow, Duration.ofSeconds(10));
    }

    private static AbstractObjectAssert<?, Object> assertOutputForTask(Execution executionResult, String taskId) {
        return assertTask(executionResult, taskId)
            .extracting(TaskRun::getOutputs).extracting(x -> x.get("value"));
    }

    private static ObjectAssert<TaskRun> assertTask(Execution executionResult, String taskId) {
        return assertThat(executionResult.getTaskRunList()).filteredOn(x -> taskId.equals(x.getTaskId())).hasSize(1).first();
    }
}
