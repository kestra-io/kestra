package io.kestra.plugin.core.flow;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class RuntimeLabelsTest extends AbstractMemoryRunnerTest {
    @Test
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
    void noNpeOnNullPreviousExecutionLabels() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "npe-labels-update-task"
        );

        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        String labelsTaskRunId = execution.findTaskRunsByTaskId("labels").getFirst().getId();
        assertThat(execution.getLabels(), hasItem(new Label("someLabel", labelsTaskRunId)));
    }
}
