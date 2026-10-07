package io.kestra.scheduler.endpoint;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.scheduler.DefaultScheduler;
import io.kestra.scheduler.internals.DefaultSchedulableTriggerFetcher;
import io.kestra.scheduler.models.TriggerEvaluationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchedulerEndpointTest {

    private static final Instant FIXED_NOW = Instant.parse("2024-06-01T10:00:00Z");
    private static final Set<Integer> VNODES = Set.of(0, 1);

    private DefaultScheduler scheduler;
    private DefaultSchedulableTriggerFetcher schedulableTriggerFetcher;
    private SchedulerEndpoint endpoint;

    @BeforeEach
    void setUp() {
        scheduler = mock(DefaultScheduler.class);
        schedulableTriggerFetcher = mock(DefaultSchedulableTriggerFetcher.class);

        when(scheduler.clock()).thenReturn(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
        when(scheduler.currentVNodesAssignment()).thenReturn(VNODES);

        endpoint = new SchedulerEndpoint(scheduler, schedulableTriggerFetcher);
    }

    @Test
    void shouldReturnEmptyResultWhenNoTriggersAreSchedulable() {
        when(schedulableTriggerFetcher.getSchedulableTriggers(any(), any(), eq(VNODES)))
            .thenReturn(List.of());

        var result = endpoint.running();

        assertThat(result.getSchedulableCount()).isZero();
        assertThat(result.getSchedulable()).isEmpty();
    }

    @Test
    void shouldReturnOneEntryPerSchedulableTrigger() {
        var first = triggerEvaluationContextOf("flow-a", "io.kestra.test", 1, "every-minute");
        var second = triggerEvaluationContextOf("flow-b", "io.kestra.test", 2, "hourly");
        when(schedulableTriggerFetcher.getSchedulableTriggers(any(), any(), eq(VNODES)))
            .thenReturn(List.of(first, second));

        var result = endpoint.running();

        assertThat(result.getSchedulableCount()).isEqualTo(2);
        assertThat(result.getSchedulable()).hasSize(2);
    }

    @Test
    void shouldMapFlowMetadataCorrectlyIntoScheduleEntry() {
        var ctx = triggerEvaluationContextOf("my-flow", "io.kestra.ns", 3, "trigger-id");
        when(schedulableTriggerFetcher.getSchedulableTriggers(any(), any(), eq(VNODES)))
            .thenReturn(List.of(ctx));

        var result = endpoint.running();

        var entry = result.getSchedulable().get(0);
        assertThat(entry.getFlowId()).isEqualTo("my-flow");
        assertThat(entry.getNamespace()).isEqualTo("io.kestra.ns");
        assertThat(entry.getRevision()).isEqualTo(3);
        assertThat(entry.getTrigger()).isSameAs(ctx.trigger());
    }

    @Test
    void shouldDeriveNextDateFromTriggerStateNextEvaluationDate() {
        var ctx = triggerEvaluationContextOf("flow-c", "io.kestra.smoke", 1, "t1");
        when(schedulableTriggerFetcher.getSchedulableTriggers(any(), any(), eq(VNODES)))
            .thenReturn(List.of(ctx));

        var result = endpoint.running();

        var expectedNext = ctx.triggerState().getNextEvaluationDate().atZone(ZoneId.systemDefault());
        assertThat(result.getSchedulable().get(0).getNext()).isEqualTo(expectedNext);
    }

    private static TriggerEvaluationContext triggerEvaluationContextOf(
        String flowId, String namespace, int revision, String triggerId
    ) {
        FlowInterface flow = mock(FlowInterface.class);
        when(flow.getId()).thenReturn(flowId);
        when(flow.getNamespace()).thenReturn(namespace);
        when(flow.getRevision()).thenReturn(revision);

        AbstractTrigger trigger = mock(AbstractTrigger.class);
        when(trigger.getId()).thenReturn(triggerId);

        TriggerState triggerState = TriggerState.builder()
            .nextEvaluationDate(FIXED_NOW.plusSeconds(60))
            .build();

        return new TriggerEvaluationContext(flow, trigger, triggerState, mock(ConditionContext.class));
    }
}
