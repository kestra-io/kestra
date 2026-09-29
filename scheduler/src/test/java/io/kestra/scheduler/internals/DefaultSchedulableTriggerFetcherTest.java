package io.kestra.scheduler.internals;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.runners.ProcessedFlow;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.services.FlowParsingService;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.trigger.Schedule;
import io.kestra.scheduler.models.TriggerEvaluationContext;
import io.kestra.scheduler.utils.InMemoryFlowMetaStore;
import io.kestra.scheduler.utils.InMemoryTriggerStateStore;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MicronautTest
class DefaultSchedulableTriggerFetcherTest {

    private static final Schedule SCHEDULE = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("* * * * *").build();

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void shouldDisableTriggerAndKeepOthersWhenFlowIsUnparseable() throws Exception {
        // Given two due triggers on the same vNode, one whose flow could not be deserialized on this version
        Clock clock = Clock.systemUTC();
        ZonedDateTime now = ZonedDateTime.now(clock);
        FlowWithSource valid = flow("valid");
        FlowWithSource broken = FlowWithException.from(flow("broken"), new FlowProcessingException("Invalid type: io.kestra.plugin.core.flow.ForEach"));
        InMemoryTriggerStateStore triggerStateStore = new InMemoryTriggerStateStore();
        TriggerState validState = TriggerState.of(valid, SCHEDULE, 0).updateForNextEvaluationDate(clock, now.minusMinutes(1));
        TriggerState brokenState = TriggerState.of(broken, SCHEDULE, 0).updateForNextEvaluationDate(clock, now.minusMinutes(1));
        triggerStateStore.save(validState);
        triggerStateStore.save(brokenState);
        FlowParsingService flowParsingService = mock(FlowParsingService.class);
        when(flowParsingService.parseForRuntime(valid)).thenReturn(ProcessedFlow.of(valid));
        when(flowParsingService.parseForRuntime(broken)).thenThrow(new FlowProcessingException("Invalid type: io.kestra.plugin.core.flow.ForEach"));
        DefaultSchedulableTriggerFetcher fetcher = new DefaultSchedulableTriggerFetcher(
            runContextFactory,
            triggerStateStore,
            new InMemoryFlowMetaStore(1, List.of(valid, broken)),
            flowParsingService
        );

        // When
        List<TriggerEvaluationContext> schedulable = fetcher.getSchedulableTriggers(clock, now, Set.of(0));

        // Then the valid trigger is still evaluated and the broken one is disabled until its flow is saved again
        assertThat(schedulable).extracting(context -> context.flow().getId()).containsExactly("valid");
        assertThat(triggerStateStore.findByIdWithoutAcl(brokenState).orElseThrow().isDisabled()).isTrue();
        assertThat(triggerStateStore.findByIdWithoutAcl(validState).orElseThrow().isDisabled()).isFalse();
    }

    @Test
    void shouldNotScheduleTriggerDisabledInDefinitionWithoutParsingTheFlow() throws Exception {
        // Given a due trigger whose definition declares `disabled: true`, while its state is enabled
        Clock clock = Clock.systemUTC();
        ZonedDateTime now = ZonedDateTime.now(clock);
        Schedule disabled = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("* * * * *").disabled(true).build();
        FlowWithSource flow = flow("disabled-in-source").toBuilder().triggers(List.of(disabled)).build();
        InMemoryTriggerStateStore triggerStateStore = new InMemoryTriggerStateStore();
        TriggerState state = TriggerState.of(flow, disabled, 0).updateForNextEvaluationDate(clock, now.minusMinutes(1));
        triggerStateStore.save(state);
        FlowParsingService flowParsingService = mock(FlowParsingService.class);
        DefaultSchedulableTriggerFetcher fetcher = new DefaultSchedulableTriggerFetcher(
            runContextFactory,
            triggerStateStore,
            new InMemoryFlowMetaStore(1, List.of(flow)),
            flowParsingService
        );

        // When
        List<TriggerEvaluationContext> schedulable = fetcher.getSchedulableTriggers(clock, now, Set.of(0));

        // Then it is skipped on the stored definition, without a parse and without touching its state
        assertThat(schedulable).isEmpty();
        verify(flowParsingService, never()).parseForRuntime(any());
        assertThat(triggerStateStore.findByIdWithoutAcl(state).orElseThrow().isDisabled()).isFalse();
    }

    @Test
    void shouldCorrectTheDefinitionMirrorWhenItDisagreesWithTheFlow() {
        // Given a state written before the mirror existed: the flow disables the trigger, the state says otherwise
        Clock clock = Clock.systemUTC();
        ZonedDateTime now = ZonedDateTime.now(clock);
        Schedule disabled = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("* * * * *").disabled(true).build();
        FlowWithSource flow = flow("stale-mirror").toBuilder().triggers(List.of(disabled)).build();
        InMemoryTriggerStateStore triggerStateStore = new InMemoryTriggerStateStore();
        TriggerState state = TriggerState.of(flow, disabled, 0)
            .sourceDisabled(clock, false)
            .updateForNextEvaluationDate(clock, now.minusMinutes(1));
        triggerStateStore.save(state);
        DefaultSchedulableTriggerFetcher fetcher = new DefaultSchedulableTriggerFetcher(
            runContextFactory,
            triggerStateStore,
            new InMemoryFlowMetaStore(1, List.of(flow)),
            mock(FlowParsingService.class)
        );

        // When
        List<TriggerEvaluationContext> schedulable = fetcher.getSchedulableTriggers(clock, now, Set.of(0));

        // Then the mirror is brought back in line, so the search filter reports the trigger as disabled
        assertThat(schedulable).isEmpty();
        assertThat(triggerStateStore.findByIdWithoutAcl(state).orElseThrow().isSourceDisabled()).isTrue();
    }

    @Test
    void shouldClearTheDefinitionMirrorWhenTheFlowNoLongerDisablesTheTrigger() throws Exception {
        // Given a state still mirroring a `disabled: true` the flow has since dropped
        Clock clock = Clock.systemUTC();
        ZonedDateTime now = ZonedDateTime.now(clock);
        Schedule enabled = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("* * * * *").build();
        FlowWithSource flow = flow("stale-mirror-set").toBuilder().triggers(List.of(enabled)).build();
        InMemoryTriggerStateStore triggerStateStore = new InMemoryTriggerStateStore();
        TriggerState state = TriggerState.of(flow, enabled, 0)
            .sourceDisabled(clock, true)
            .updateForNextEvaluationDate(clock, now.minusMinutes(1));
        triggerStateStore.save(state);
        FlowParsingService flowParsingService = mock(FlowParsingService.class);
        when(flowParsingService.parseForRuntime(flow)).thenReturn(ProcessedFlow.of(flow));
        DefaultSchedulableTriggerFetcher fetcher = new DefaultSchedulableTriggerFetcher(
            runContextFactory,
            triggerStateStore,
            new InMemoryFlowMetaStore(1, List.of(flow)),
            flowParsingService
        );

        // When
        List<TriggerEvaluationContext> schedulable = fetcher.getSchedulableTriggers(clock, now, Set.of(0));

        // Then the mirror is cleared and the trigger is scheduled again
        assertThat(triggerStateStore.findByIdWithoutAcl(state).orElseThrow().isSourceDisabled()).isFalse();
        assertThat(schedulable).hasSize(1);
    }

    private static FlowWithSource flow(String id) {
        return FlowWithSource.builder()
            .tenantId("main")
            .namespace("io.kestra.tests")
            .id(id)
            .revision(1)
            .tasks(List.of(Return.builder().id("return").type(Return.class.getName()).build()))
            .triggers(List.of(SCHEDULE))
            .build();
    }
}
