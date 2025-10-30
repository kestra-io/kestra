package io.kestra.plugin.core.flow;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import java.io.File;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
public class DagTest {

    @Inject
    ModelValidator modelValidator;

    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    private FlowInputOutput flowIO;

    @Test
    @ExecuteFlow("flows/valids/dag.yaml")
    void dag(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList().size()).isEqualTo(7);
    }

    @Test
    void dagCyclicDependencies() {
        Flow flow = this.parse("flows/invalids/dag-cyclicdependency.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent()).isTrue();
        assertThat(validate.get().getConstraintViolations().size()).isEqualTo(1);

        assertThat(validate.get().getMessage()).contains("dag: Cyclic dependency detected: task1, task2");
    }

    @Test
    void dagNotExistTask() {
        Flow flow = this.parse("flows/invalids/dag-notexist-task.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent()).isTrue();
        assertThat(validate.get().getConstraintViolations().size()).isEqualTo(1);

        assertThat(validate.get().getMessage()).contains("dag: Not existing task id in dependency: taskX");
    }

    @Test
    @LoadFlows({"flows/valids/finally-dag.yaml"})
    void errors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            "io.kestra.tests", "finally-dag", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(9);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getStartDate().isAfter(execution.findTaskRunsByTaskId("a1").getFirst().getState().getEndDate().orElseThrow())).isTrue();
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getStartDate().isAfter(execution.findTaskRunsByTaskId("e1").getFirst().getState().getEndDate().orElseThrow())).isTrue();
    }

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return YamlParser.parse(file, Flow.class);
    }
}
