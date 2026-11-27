package io.kestra.executor.statemachine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.flows.sla.types.MaxDurationSLA;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;

class QueuedExecutionSLATest {
    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    @ParameterizedTest
    @EnumSource(value = SLA.Behavior.class, names = { "CANCEL", "FAIL" })
    void shouldKeepSlotAndQueueWhenSlaEndsQueuedExecution(SLA.Behavior behavior) {
        // Given: a holder running on the only slot, then two queued executions
        var sla = MaxDurationSLA.builder().id("max-duration").type(SLA.Type.MAX_DURATION).behavior(behavior).duration(Duration.ofMillis(1)).build();
        FlowWithSource flow = Flows.of(
            Flows.builder(Flows.log("a"))
                .concurrency(Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build())
                .sla(List.of(sla))
                .build()
        );
        harness.registerFlow(flow);
        Execution holder = Executions.created(flow);
        // started an hour ago: duration SLAs are measured on the wall clock, a just-created execution may not have exceeded even 1ms yet
        Execution expired = Executions.createdAt(flow, Instant.now().minus(Duration.ofHours(1)));
        Execution waiting = Executions.created(flow);
        harness.step(holder);
        harness.step(expired);
        harness.step(waiting);
        harness.slaMonitorStateStore().purge(holder.getId());
        harness.slaMonitorStateStore().purge(waiting.getId());
        assertThat(harness).as("before the deadline").hasExecutionInState(holder, State.Type.RUNNING).hasQueuedExactly(expired, waiting).hasRunning(flow, 1);

        // When: only the first queued execution reaches its SLA deadline
        harness.tickSlaMonitors(Instant.now().plus(Duration.ofMinutes(1)));

        // Then: it never held a slot, so its end releases nothing and pops nobody
        assertThat(harness).hasQueuedExactly(waiting).hasRunning(flow, 1);
    }
}
