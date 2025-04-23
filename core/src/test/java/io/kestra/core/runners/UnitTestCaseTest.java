package io.kestra.core.runners;

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
import jakarta.inject.Singleton;
import org.assertj.core.api.AbstractObjectAssert;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Singleton
public class UnitTestCaseTest {

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    protected FlowRepositoryInterface flowRepository;

    @Inject
    protected ApplicationContext applicationContext;

    public void allUnitTestTestCases() throws Exception {
        withoutAnyTaskFixture();
        taskFixture();
    }

    public void withoutAnyTaskFixture() throws QueueException, TimeoutException {
        var fixtures = List.<TaskFixture>of();

        var executionResult = runReturnFlow(fixtures);

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

    public void taskFixture() throws TimeoutException, QueueException {
        var fixtures = List.of(
            TaskFixture.builder()
                .id("date")
                .build()
        );

        var executionResult = runReturnFlow(fixtures);

        assertThat(executionResult.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertOutputForTask(executionResult, "task-id")
            .isEqualTo("task-id");
        assertOutputForTask(executionResult, "flow-id")
            .isEqualTo("return");
        assertOutputForTask(executionResult, "date")
            .isNull();
    }

    public void twoTaskFixturesOverridingOutput() throws QueueException, TimeoutException {
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

        var executionResult = runReturnFlow(fixtures);

        assertThat(executionResult.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertOutputForTask(executionResult, "task-id")
            .isEqualTo("task-id");
        assertOutputForTask(executionResult, "flow-id")
            .isEqualTo("my-mocked-output-flow-id");
        assertOutputForTask(executionResult, "date")
            .isEqualTo("my-mocked-output-value");
    }

    private Execution runReturnFlow(List<TaskFixture> fixtures) throws TimeoutException, QueueException {
        var flow = flowRepository.findById(null, "io.kestra.tests", "return", Optional.empty()).orElseThrow();

        var execution = Execution.builder()
            .id(IdUtils.create())
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .fixtures(fixtures)
            .state(new State())
            .build();

        return runnerUtils.runOne(execution, flow, null);
    }

    private static AbstractObjectAssert<?, Object> assertOutputForTask(Execution executionResult, String taskId) {
        return assertThat(executionResult.getTaskRunList()).filteredOn(x -> taskId.equals(x.getTaskId()))
            .extracting(TaskRun::getOutputs).first().extracting(x -> x.get("value"));
    }
}
