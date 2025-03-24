package io.kestra.plugin.core.flow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.RunnerUtils;
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
    YamlParser yamlParser = new YamlParser();

    @Inject
    ModelValidator modelValidator;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    private FlowInputOutput flowIO;

    @Test
    @ExecuteFlow("flows/valids/dag.yaml")
    void dag(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList().size(), is(7));
    }

    @Test
    void dagCyclicDependencies() {
        Flow flow = this.parse("flows/invalids/dag-cyclicdependency.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), containsString("dag: Cyclic dependency detected: task1, task2"));
    }

    @Test
    void dagNotExistTask() {
        Flow flow = this.parse("flows/invalids/dag-notexist-task.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), containsString("dag: Not existing task id in dependency: taskX"));
    }

    @Test
    @LoadFlows({"flows/valids/finally-dag.yaml"})
    void errors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-dag", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(9));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getStartDate().isAfter(execution.findTaskRunsByTaskId("a1").getFirst().getState().getEndDate().orElseThrow()), is(true));
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getStartDate().isAfter(execution.findTaskRunsByTaskId("e1").getFirst().getState().getEndDate().orElseThrow()), is(true));
    }

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return yamlParser.parse(file, Flow.class);
    }
}
