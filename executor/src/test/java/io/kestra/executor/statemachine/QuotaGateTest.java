package io.kestra.executor.statemachine;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.quota.Quota;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.plugin.core.log.Log;

import static io.kestra.executor.testkit.ExecutorContextAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/**
 * Layer-1 sagas of the quota gate in {@code ExecutionEventMessageHandler}: when the gate is
 * consulted, how the FAIL/CANCEL behaviors stop an execution, and that an exceeded quota
 * short-circuits before the concurrency gate. Quota <em>evaluation</em> is an EE feature — the
 * OSS {@code QuotaService} only accepts quota-less flows — so the harness's Mockito stub plays
 * the EE implementation while the gate logic under test is pure OSS executor code. No Micronaut, no
 * database. The EE integration twins live in the enterprise repository.
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
        ExecutorContext context = startExecution(flow);

        // Then: FAILED terminal before any task run is created
        assertThat(context).executionInState(State.Type.FAILED).updatedFrom("processQuotas");
        Assertions.assertThat(context.getExecution().getTaskRunList()).isNullOrEmpty();
    }

    @Test
    void shouldCancelExecutionWhenQuotaExceededWithCancelBehavior() {
        // Given
        FlowWithSource flow = quotaFlow(Quota.Behavior.CANCEL);
        harness.registerFlow(flow);
        doReturn(Optional.of(quota(Quota.Behavior.CANCEL))).when(harness.quotaService()).checkAndIncrement(any());

        // When
        ExecutorContext context = startExecution(flow);

        // Then
        assertThat(context).executionInState(State.Type.CANCELLED).updatedFrom("processQuotas");
        Assertions.assertThat(context.getExecution().getTaskRunList()).isNullOrEmpty();
    }

    // --- gate ordering

    @Test
    void shouldStopBeforeConcurrencyGateWhenQuotaExceeded() {
        // Given: a flow with a free concurrency slot AND an exceeded quota
        FlowWithSource flow = Flows.of(
            Flows.builder(logTask())
                .quotas(List.of(quota(Quota.Behavior.CANCEL)))
                .concurrency(Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build())
                .build()
        );
        harness.registerFlow(flow);
        doReturn(Optional.of(quota(Quota.Behavior.CANCEL))).when(harness.quotaService()).checkAndIncrement(any());

        // When
        ExecutorContext context = startExecution(flow);

        // Then: the quota gate wins — stopped by quota, the free slot is never claimed and
        // nothing reaches the queued store
        assertThat(context).executionInState(State.Type.CANCELLED).updatedFrom("processQuotas");
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(0);
        Assertions.assertThat(harness.executionQueuedStateStore().queued()).isEmpty();
    }

    // --- passing through the gate

    @Test
    void shouldClaimConcurrencySlotWhenQuotasAreWithinLimits() {
        // Given: quotas configured but none exceeded
        FlowWithSource flow = Flows.of(
            Flows.builder(logTask())
                .quotas(List.of(quota(Quota.Behavior.FAIL)))
                .concurrency(Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build())
                .build()
        );
        harness.registerFlow(flow);
        doReturn(Optional.empty()).when(harness.quotaService()).checkAndIncrement(any());

        // When
        ExecutorContext context = startExecution(flow);

        // Then: the gate is pass-through — the execution runs and claims its concurrency slot
        Assertions.assertThat(context.getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
        verify(harness.quotaService()).checkAndIncrement(any());
    }

    @Test
    void shouldConsultQuotaServiceEvenWhenFlowHasNoQuotas() {
        // Given: no quotas on the flow. Since namespace/tenant quotas exist, the gate always
        // consults the service — flow-level quotas are no longer the only source. The OSS
        // QuotaService is safe for quota-less flows (it only throws when the flow defines
        // quotas; EE @Replaces it for the rest).
        FlowWithSource flow = Flows.of(logTask());
        harness.registerFlow(flow);
        doReturn(Optional.empty()).when(harness.quotaService()).checkAndIncrement(any());

        // When
        ExecutorContext context = startExecution(flow);

        // Then: consulted, and pass-through when nothing is exceeded
        Assertions.assertThat(context.getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
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
        harness.executionStateStore().save(restarted);

        // When: the restart event enters the gate (the failedThenRestarted branch)
        ExecutorContext context = handleEvent(restarted);

        // Then: a restart consumes quota like a fresh execution
        assertThat(context).executionInState(State.Type.CANCELLED).updatedFrom("processQuotas");
        verify(harness.quotaService()).checkAndIncrement(any());
    }

    // --- fixtures

    private ExecutorContext startExecution(FlowWithSource flow) {
        Execution execution = Executions.created(flow);
        harness.executionStateStore().save(execution);
        return handleEvent(execution);
    }

    private ExecutorContext handleEvent(Execution execution) {
        return harness.executionEventMessageHandler()
            .handle(new ExecutionEvent(execution, ExecutionEventType.CREATED))
            .orElseThrow();
    }

    private static FlowWithSource quotaFlow(Quota.Behavior behavior) {
        return Flows.of(Flows.builder(logTask()).quotas(List.of(quota(behavior))).build());
    }

    private static Quota quota(Quota.Behavior behavior) {
        return Quota.builder().duration(Duration.ofHours(1)).limit(10L).behavior(behavior).build();
    }

    private static Log logTask() {
        return Log.builder().id("log").type(Log.class.getName()).message("hello").build();
    }
}
