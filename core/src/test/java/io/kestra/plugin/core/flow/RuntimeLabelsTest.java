package io.kestra.plugin.core.flow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunnerUtils;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
class RuntimeLabelsTest {

    @Inject
    private RunnerUtils runnerUtils;

    @Test
    @LoadFlows({"flows/valids/labels-update-task.yml"})
    void update() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "labels-update-task",
            null,
            (flow, createdExecution) -> Map.of(
                "labelsJson", "{\"keyFromJson\": \"valueFromJson\"}",
                "labelsMapKey", "keyFromMap",
                "labelsMapValue", "valueFromMap",
                "labelsListKey", "keyFromList",
                "labelsListValue", "valueFromList"
            ),
            null,
            List.of(
                new Label("keyFromExecution", "valueFromExecution"),
                new Label("overriddenExecutionLabelKey", "executionValueThatWillGetOverridden")
            )
        );

        assertThat(execution.getTaskRunList().size(), is(4));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        String labelsOverriderTaskRunId = execution.findTaskRunsByTaskId("override-labels").getFirst().getId();
        assertThat(execution.getLabels(), containsInAnyOrder(
            is(new Label(Label.CORRELATION_ID, execution.getId())),
            is(new Label("flowLabelKey", "flowLabelValue")),
            is(new Label("overriddenFlowLabelKey", "io.kestra.tests.labels-update-task")),
            is(new Label("keyFromJson", "valueFromJson")),
            is(new Label("keyFromMap", "valueFromMap")),
            is(new Label("keyFromList", "valueFromList")),
            is(new Label("keyFromExecution", "valueFromExecution")),
            is(new Label("overriddenExecutionLabelKey", labelsOverriderTaskRunId))
        ));
    }


    @Test
    @ExecuteFlow("flows/valids/npe-labels-update-task.yml")
    void noNpeOnNullPreviousExecutionLabels(Execution execution) {
        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        String labelsTaskRunId = execution.findTaskRunsByTaskId("labels").getFirst().getId();
        assertThat(execution.getLabels(), hasItem(new Label("someLabel", labelsTaskRunId)));
    }

    @Test
    @LoadFlows({"flows/valids/primitive-labels-flow.yml"})
    void primitiveTypeLabels() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "primitive-labels-flow",
            null,
            (flow, createdExecution) -> Map.of(
                "intLabel", 42,
                "boolLabel", true,
                "floatLabel", 3.14f
            ),
            null,
            List.of(
                new Label("existingLabel", "someValue")
            )
        );

        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        String labelsTaskRunId = execution.findTaskRunsByTaskId("update-labels").getFirst().getId();

        assertThat(execution.getLabels(), containsInAnyOrder(
            is(new Label(Label.CORRELATION_ID, execution.getId())),
            is(new Label("intValue", "42")),
            is(new Label("boolValue", "true")),
            is(new Label("floatValue", "3.14")),
            is(new Label("taskRunId", labelsTaskRunId)),
            is(new Label("existingLabel", "someValue"))
        ));
    }
}
