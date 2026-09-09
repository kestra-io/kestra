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
import static org.mockito.Mockito.mock;
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
