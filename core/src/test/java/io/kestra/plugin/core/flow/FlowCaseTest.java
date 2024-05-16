package io.kestra.plugin.core.flow;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunnerUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Singleton
public class FlowCaseTest {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    QueueInterface<Execution> executionQueue;

    @Inject
    protected RunnerUtils runnerUtils;

    public void waitSuccess() throws Exception {
        this.run("OK", State.Type.SUCCESS, State.Type.SUCCESS, 2, "default > amazing", true);
    }

    public void waitFailed() throws Exception {
        this.run("THIRD", State.Type.FAILED, State.Type.FAILED, 4, "Error Trigger ! error-t1", true);
    }

    public void invalidOutputs() throws Exception {
        this.run("FIRST", State.Type.FAILED, State.Type.SUCCESS, 2, null, true);
    }

    public void noLabels() throws Exception {
        this.run("OK", State.Type.SUCCESS, State.Type.SUCCESS, 2, "default > amazing", false);
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored", "unchecked"})
    void run(String input, State.Type fromState, State.Type triggerState, int count, String outputs, boolean testInherited) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicReference<Execution> triggered = new AtomicReference<>();

        executionQueue.receive(either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("switch") && execution.getState().getCurrent().isTerminated()) {
                countDownLatch.countDown();
                triggered.set(execution);
            }
        });

        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            testInherited ? "task-flow" : "task-flow-inherited-labels",
            null,
            (f, e) -> ImmutableMap.of("string", input),
            Duration.ofMinutes(1),
            testInherited ? List.of(new Label("mainFlowExecutionLabel", "execFoo")) : List.of()
        );

        countDownLatch.await(1, TimeUnit.MINUTES);

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getAttempts(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getAttempts().get(0).getState().getCurrent(), is(fromState));
        assertThat(execution.getState().getCurrent(), is(fromState));

        if (outputs != null) {
            assertThat(((Map<String, String>) execution.getTaskRunList().get(0).getOutputs().get("outputs")).get("extracted"), containsString(outputs));
        }

        assertThat(execution.getTaskRunList().get(0).getOutputs().get("executionId"), is(triggered.get().getId()));

        if (outputs != null) {
            assertThat(execution.getTaskRunList().get(0).getOutputs().get("state"), is(triggered.get().getState().getCurrent().name()));
        }

        assertThat(triggered.get().getTrigger().getType(), is("io.kestra.core.tasks.flows.Subflow"));
        assertThat(triggered.get().getTrigger().getVariables().get("executionId"), is(execution.getId()));
        assertThat(triggered.get().getTrigger().getVariables().get("flowId"), is(execution.getFlowId()));
        assertThat(triggered.get().getTrigger().getVariables().get("namespace"), is(execution.getNamespace()));

        assertThat(triggered.get().getTaskRunList(), hasSize(count));
        assertThat(triggered.get().getState().getCurrent(), is(triggerState));

        if (testInherited) {
            assertThat(triggered.get().getLabels(), hasItems(
                new Label("mainFlowExecutionLabel", "execFoo"),
                new Label("mainFlowLabel", "flowFoo"),
                new Label("launchTaskLabel", "launchFoo"),
                new Label("switchFlowLabel", "switchFoo")
            ));
        } else {
            assertThat(triggered.get().getLabels().size(), is(2));
            assertThat(triggered.get().getLabels(), hasItems(
                new Label("launchTaskLabel", "launchFoo"),
                new Label("switchFlowLabel", "switchFoo")
            ));
        }
    }
}
