package io.kestra.executor.statemachine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.types.MaxDurationSLA;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;

import static org.assertj.core.api.Assertions.assertThat;

class QueuedExecutionSLATest {
    @ParameterizedTest
    @EnumSource(SLA.Behavior.class)
    void shouldApplyExpiredSLABeforeClaimingASlot(SLA.Behavior behavior) {
        ExecutorTestHarness harness = ExecutorTestHarness.create();
        Label label = new Label("sla", "violated");
        FlowWithSource flow = Flows.of(
            Flows.builder(Flows.log())
                .concurrency(Concurrency.builder().limit(1).behavior(Concurrency.Behavior.QUEUE).build())
                .sla(
                    List.of(
                        MaxDurationSLA.builder()
                            .id("maxdur").type(SLA.Type.MAX_DURATION).duration(Duration.ofHours(1)).behavior(behavior).labels(List.of(label)).build()
                    )
                )
                .build()
        );
        harness.registerFlow(flow);
        Execution holder = Executions.created(flow);
        Execution expired = Executions.created(flow).toBuilder()
            .state(new State(State.Type.CREATED, List.of(new State.History(State.Type.CREATED, Instant.now().minus(Duration.ofHours(2))))))
            .build();
        Execution eligible = Executions.created(flow);
        harness.step(holder);
        harness.step(expired);
        harness.step(eligible);
        Execution running = harness.executionStateStore().findByIdWithoutAcl(holder.getId());

        List<Execution> popped = harness.concurrencySlotReleaseProcessor().release(
            new ExecutorContext(running, flow).withExecution(running.withState(State.Type.SUCCESS), "test"), true
        );

        Execution processed = popped.getFirst();
        assertThat(processed.getTaskRunList()).isNullOrEmpty();
        assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
        if (behavior == SLA.Behavior.NONE) {
            assertThat(popped).extracting(Execution::getId).containsExactly(expired.getId());
            assertThat(processed.getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        } else {
            assertThat(popped).extracting(Execution::getId).containsExactly(expired.getId(), eligible.getId());
            assertThat(processed.getState().getCurrent()).isEqualTo(behavior == SLA.Behavior.FAIL ? State.Type.FAILED : State.Type.CANCELLED);
            assertThat(processed.getState().getHistories()).extracting(State.History::getState).doesNotContain(State.Type.RUNNING);
            assertThat(popped.getLast().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
            assertThat(popped.getLast().getLabels()).doesNotContain(label);
        }
        assertThat(processed.getLabels()).contains(label);
    }
}
