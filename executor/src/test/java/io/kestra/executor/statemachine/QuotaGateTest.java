package io.kestra.executor.statemachine;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.quota.Quota;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;

import static io.kestra.executor.testkit.ExecutorContextAssert.assertThat;
import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/**
 * Layer-1 sagas of the quota gate in {@code ExecutionEventMessageHandler}: when the gate is
 */
class QuotaGateTest {

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    // --- stopping at the gate

    @Test
    void shouldFailExecutionWhenQuotaExceededWithFailBehavior() {
        // Given
        FlowWithSource flow = quotaFlow(Quota.Behavior.FAIL);
        harness.registerFlow(flow);
        doReturn(Optional.of(quota(Quota.Behavior.FAIL))).when(harness.quotaService()).checkAndIncrement(any());

        // When
        ExecutorContext context = harness.start(flow);

        assertThat(context).as("FAILED terminal before any task run is created").executionInState(State.Type.FAILED).updatedFrom("processQuotas");
        assertThat(context).hasNoTaskRuns();
    }

    @Test
    void shouldCancelExecutionWhenQuotaExceededWithCancelBehavior() {
        // Given
        FlowWithSource flow = quotaFlow(Quota.Behavior.CANCEL);
        harness.registerFlow(flow);
        doReturn(Optional.of(quota(Quota.Behavior.CANCEL))).when(harness.quotaService()).checkAndIncrement(any());

        // When
        ExecutorContext context = harness.start(flow);
        assertThat(context).executionInState(State.Type.CANCELLED).updatedFrom("processQuotas");
        assertThat(context).hasNoTaskRuns();
    }

    // --- gate ordering

    @Test
    void shouldStopBeforeConcurrencyGateWhenQuotaExceeded() {
        // Given: a flow with a free concurrency slot AND an exceeded quota
        FlowWithSource flow = Flows.of(
            Flows.builder(Flows.log())
                .quotas(List.of(quota(Quota.Behavior.CANCEL)))
                .concurrency(Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build())
                .build()
        );
        harness.registerFlow(flow);
        doReturn(Optional.of(quota(Quota.Behavior.CANCEL))).when(harness.quotaService()).checkAndIncrement(any());

        // When
        ExecutorContext context = harness.start(flow);

        assertThat(context).as("the quota gate wins — stopped by quota, the free slot is never claimed and nothing reaches the queued store").executionInState(State.Type.CANCELLED)
            .updatedFrom("processQuotas");
        assertThat(harness).hasRunning(flow, 0);
        assertThat(harness).hasNothingQueued();
    }

    // --- passing through the gate

    @Test
    void shouldClaimConcurrencySlotWhenQuotasAreWithinLimits() {
        // Given: quotas configured but none exceeded
        FlowWithSource flow = Flows.of(
            Flows.builder(Flows.log())
                .quotas(List.of(quota(Quota.Behavior.FAIL)))
                .concurrency(Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build())
                .build()
        );
        harness.registerFlow(flow);
        doReturn(Optional.empty()).when(harness.quotaService()).checkAndIncrement(any());

        // When
        ExecutorContext context = harness.start(flow);

        assertThat(context).as("the gate is pass-through — the execution runs and claims its concurrency slot").executionInState(State.Type.RUNNING);
        assertThat(harness).hasRunning(flow, 1);
        verify(harness.quotaService()).checkAndIncrement(any());
    }

    @Test
    void shouldConsultQuotaServiceEvenWhenFlowHasNoQuotas() {
        // Given: no quotas on the flow. Since namespace/tenant quotas exist, the gate always
        // consults the service — flow-level quotas are no longer the only source. The OSS
        // QuotaService is safe for quota-less flows (it only throws when the flow defines
        // quotas; EE @Replaces it for the rest).
        FlowWithSource flow = Flows.of(Flows.log());
        harness.registerFlow(flow);
        doReturn(Optional.empty()).when(harness.quotaService()).checkAndIncrement(any());

        // When
        ExecutorContext context = harness.start(flow);

        assertThat(context).as("consulted, and pass-through when nothing is exceeded").executionInState(State.Type.RUNNING);
        verify(harness.quotaService()).checkAndIncrement(any());
    }

    // --- re-entry

    @Test
    void shouldRecheckQuotaWhenFailedExecutionIsRestarted() {
        // Given: a previously FAILED execution being RESTARTED against an exhausted quota
        FlowWithSource flow = quotaFlow(Quota.Behavior.CANCEL);
        harness.registerFlow(flow);
        doReturn(Optional.of(quota(Quota.Behavior.CANCEL))).when(harness.quotaService()).checkAndIncrement(any());
        Execution restarted = Executions.created(flow)
            .withState(State.Type.FAILED)
            .withState(State.Type.RESTARTED);
        // When: the restart event enters the gate (the failedThenRestarted branch)
        ExecutorContext context = harness.handle(restarted);

        assertThat(context).as("a restart consumes quota like a fresh execution").executionInState(State.Type.CANCELLED).updatedFrom("processQuotas");
        verify(harness.quotaService()).checkAndIncrement(any());
    }

    // --- fixtures

    private static FlowWithSource quotaFlow(Quota.Behavior behavior) {
        return Flows.of(Flows.builder(Flows.log()).quotas(List.of(quota(behavior))).build());
    }

    private static Quota quota(Quota.Behavior behavior) {
        return Quota.builder().duration(Duration.ofHours(1)).limit(10L).behavior(behavior).build();
    }
}
