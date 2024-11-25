package io.kestra.core.runners;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Singleton
public class SLATestCase {
    @Inject
    private RunnerUtils runnerUtils;

    public void maxDurationSLAShouldFail() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "sla-max-duration-fail");

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    public void maxDurationSLAShouldPass() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "sla-max-duration-ok");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    public void executionConditionSLAShouldPass() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "sla-execution-condition");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    public void executionConditionSLAShouldCancel() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "sla-execution-condition", null, (f, e) -> Map.of("string", "CANCEL"));

        assertThat(execution.getState().getCurrent(), is(State.Type.CANCELLED));
    }

    public void executionConditionSLAShouldLabel() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "sla-execution-condition", null, (f, e) -> Map.of("string", "LABEL"));

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getLabels(), hasItem(new Label("system.sla", "violated")));
    }
}
