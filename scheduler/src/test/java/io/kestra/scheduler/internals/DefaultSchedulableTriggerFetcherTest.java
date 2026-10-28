package io.kestra.scheduler.internals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.exceptions.FlowBlockedException;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.Backfill;
import io.kestra.core.models.triggers.RecoverMissedSchedules;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.ProcessedFlow;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.scheduler.model.Blocker;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.services.FlowParsingService;
import io.kestra.core.utils.TestsUtils;
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

    @Inject
    DispatchQueueInterface<LogEntry> logQueue;

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

    @Test
    void shouldReportABlockOnceWhileItLasts() throws Exception {
        // Given a due schedule whose flow governance rejects
        Clock clock = Clock.fixed(Instant.parse("2026-10-27T10:00:30Z"), ZoneOffset.UTC);
        ZonedDateTime now = ZonedDateTime.now(clock);
        FlowWithSource flow = flow("blocked");
        TriggerState state = TriggerState.of(flow, SCHEDULE, 0).updateForNextEvaluationDate(clock, now.minusMinutes(1));
        InMemoryTriggerStateStore triggerStateStore = new InMemoryTriggerStateStore();
        triggerStateStore.save(state);
        FlowBlockedException block = new FlowBlockedException("Log plugin is not allowed by policy", List.of("deny-log"));
        FlowParsingService flowParsingService = mock(FlowParsingService.class);
        when(flowParsingService.parseForRuntime(flow)).thenThrow(block);
        DefaultSchedulableTriggerFetcher fetcher = fetcher(triggerStateStore, flow, flowParsingService);
        List<LogEntry> logs = listenToLogs(flow);

        // When the scheduler loops three times
        for (int i = 0; i < 3; i++) {
            assertThat(fetcher.getSchedulableTriggers(clock, now, Set.of(0))).isEmpty();
        }

        // Then the block is recorded and reported once, and the trigger keeps its date
        TriggerState saved = triggerStateStore.findByIdWithoutAcl(state).orElseThrow();
        assertThat(saved.getBlockedBy().ids()).containsExactly("deny-log");
        assertThat(saved.getNextEvaluationDate()).isEqualTo(state.getNextEvaluationDate());
        assertThat(warnings(logs)).singleElement()
            .satisfies(log -> assertThat(log.getMessage()).contains("Log plugin is not allowed by policy"));
    }

    @Test
    void shouldReportABlockAgainOnlyWhenItChanges() throws Exception {
        // Given a due schedule blocked by a policy, then by another, then by checks failing with a different cause each time
        Clock clock = Clock.fixed(Instant.parse("2026-10-27T10:00:30Z"), ZoneOffset.UTC);
        ZonedDateTime now = ZonedDateTime.now(clock);
        FlowWithSource flow = flow("blocked-changing");
        TriggerState state = TriggerState.of(flow, SCHEDULE, 0).updateForNextEvaluationDate(clock, now.minusMinutes(1));
        InMemoryTriggerStateStore triggerStateStore = new InMemoryTriggerStateStore();
        triggerStateStore.save(state);
        FlowParsingService flowParsingService = mock(FlowParsingService.class);
        when(flowParsingService.parseForRuntime(flow)).thenThrow(
            new FlowBlockedException("Log plugin is not allowed by policy", List.of("deny-log")),
            new FlowBlockedException("Log plugin is not allowed by policy", List.of("deny-log")),
            new FlowBlockedException("Shell tasks are not allowed by policy", List.of("deny-shell")),
            new FlowBlockedException("Governance policy resolution failed: connection reset", new RuntimeException()),
            new FlowBlockedException("Governance policy resolution failed: read timed out", new RuntimeException())
        );
        DefaultSchedulableTriggerFetcher fetcher = fetcher(triggerStateStore, flow, flowParsingService);
        List<LogEntry> logs = listenToLogs(flow);

        // When the scheduler loops through every block
        for (int i = 0; i < 5; i++) {
            fetcher.getSchedulableTriggers(clock, now, Set.of(0));
        }

        // Then each new block is reported once, a failed check whatever its cause
        assertThat(warnings(logs)).extracting(LogEntry::getMessage).satisfiesExactlyInAnyOrder(
            message -> assertThat(message).contains("Log plugin is not allowed by policy"),
            message -> assertThat(message).contains("Shell tasks are not allowed by policy"),
            message -> assertThat(message).contains("connection reset")
        );
    }

    @Test
    void shouldReplayTheDatesMissedWhileBlockedOnceUnblockedByDefault() throws Exception {
        // Given a schedule blocked since its due date, an hour ago, whose flow governance now accepts
        Clock clock = Clock.fixed(Instant.parse("2026-10-27T10:00:30Z"), ZoneOffset.UTC);
        ZonedDateTime now = ZonedDateTime.now(clock);
        FlowWithSource flow = flow("unblocked");
        TriggerState state = TriggerState.of(flow, SCHEDULE, 0)
            .updateForNextEvaluationDate(clock, now.minusHours(1))
            .blockedBy(clock, policyBlock());
        InMemoryTriggerStateStore triggerStateStore = new InMemoryTriggerStateStore();
        triggerStateStore.save(state);
        DefaultSchedulableTriggerFetcher fetcher = fetcher(triggerStateStore, flow, acceptingParser(flow));
        List<LogEntry> logs = listenToLogs(flow);

        // When
        List<TriggerEvaluationContext> schedulable = fetcher.getSchedulableTriggers(clock, now, Set.of(0));

        // Then the block is cleared and the trigger is evaluated from the date it was blocked on
        assertThat(schedulable).singleElement().satisfies(context -> {
            assertThat(context.triggerState().getBlockedBy()).isNull();
            assertThat(context.triggerState().getNextEvaluationDate()).isEqualTo(state.getNextEvaluationDate());
        });
        assertThat(TestsUtils.awaitLogs(logs, 1).getFirst().getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void shouldSkipTheDatesMissedWhileBlockedOnceUnblockedWhenTheScheduleRecoversNone() throws Exception {
        // Given a schedule recovering no missed date, blocked since an hour ago, whose flow governance now accepts
        Clock clock = Clock.fixed(Instant.parse("2026-10-27T10:00:30Z"), ZoneOffset.UTC);
        ZonedDateTime now = ZonedDateTime.now(clock);
        Schedule schedule = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("* * * * *").recoverMissedSchedules(RecoverMissedSchedules.NONE).build();
        FlowWithSource flow = flow("unblocked-none").toBuilder().triggers(List.of(schedule)).build();
        TriggerState state = TriggerState.of(flow, schedule, 0)
            .updateForNextEvaluationDate(clock, now.minusHours(1))
            .blockedBy(clock, policyBlock());
        InMemoryTriggerStateStore triggerStateStore = new InMemoryTriggerStateStore();
        triggerStateStore.save(state);
        DefaultSchedulableTriggerFetcher fetcher = fetcher(triggerStateStore, flow, acceptingParser(flow));

        // When
        List<TriggerEvaluationContext> schedulable = fetcher.getSchedulableTriggers(clock, now, Set.of(0));

        // Then the block is cleared and the trigger waits for its next slot
        assertThat(schedulable).isEmpty();
        TriggerState saved = triggerStateStore.findByIdWithoutAcl(state).orElseThrow();
        assertThat(saved.getBlockedBy()).isNull();
        assertThat(saved.getNextEvaluationDate()).isEqualTo(Instant.parse("2026-10-27T10:01:00Z"));
    }

    @Test
    void shouldReplayOnlyTheLastDateMissedWhileBlockedOnceUnblockedWhenTheScheduleRecoversLast() throws Exception {
        // Given a schedule recovering the last missed date, evaluated and blocked an hour ago, whose flow governance now accepts
        Clock clock = Clock.systemUTC();
        ZonedDateTime now = ZonedDateTime.now(clock);
        Schedule schedule = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("* * * * *").recoverMissedSchedules(RecoverMissedSchedules.LAST).build();
        FlowWithSource flow = flow("unblocked-last").toBuilder().triggers(List.of(schedule)).build();
        TriggerState state = TriggerState.of(flow, schedule, 0)
            .evaluatedAt(clock, now.minusHours(1))
            .updateForNextEvaluationDate(clock, now.minusHours(1).plusMinutes(1))
            .blockedBy(clock, policyBlock());
        InMemoryTriggerStateStore triggerStateStore = new InMemoryTriggerStateStore();
        triggerStateStore.save(state);
        DefaultSchedulableTriggerFetcher fetcher = fetcher(triggerStateStore, flow, acceptingParser(flow));

        // When
        List<TriggerEvaluationContext> schedulable = fetcher.getSchedulableTriggers(clock, now, Set.of(0));

        // Then the block is cleared and the trigger is evaluated from the last missed slot only
        assertThat(schedulable).singleElement().satisfies(context -> {
            assertThat(context.triggerState().getBlockedBy()).isNull();
            assertThat(context.triggerState().getNextEvaluationDate())
                .isAfter(now.minusMinutes(1).toInstant())
                .isBeforeOrEqualTo(now.toInstant());
        });
    }

    @Test
    void shouldResumeABackfillWhereItStoppedOnceUnblocked() throws Exception {
        // Given a blocked schedule with a backfill in progress, whose flow governance now accepts
        Clock clock = Clock.fixed(Instant.parse("2026-10-27T10:00:30Z"), ZoneOffset.UTC);
        ZonedDateTime now = ZonedDateTime.now(clock);
        FlowWithSource flow = flow("unblocked-backfill");
        TriggerState state = TriggerState.of(flow, SCHEDULE, 0)
            .updateForNextEvaluationDate(clock, now.minusDays(2))
            .backfill(clock, Backfill.builder().start(now.minusDays(2)).end(now.minusDays(1)).build())
            .blockedBy(clock, policyBlock());
        InMemoryTriggerStateStore triggerStateStore = new InMemoryTriggerStateStore();
        triggerStateStore.save(state);
        DefaultSchedulableTriggerFetcher fetcher = fetcher(triggerStateStore, flow, acceptingParser(flow));

        // When
        List<TriggerEvaluationContext> schedulable = fetcher.getSchedulableTriggers(clock, now, Set.of(0));

        // Then the backfill is evaluated from the date it was blocked on
        assertThat(schedulable).singleElement().satisfies(context -> {
            assertThat(context.triggerState().getBlockedBy()).isNull();
            assertThat(context.triggerState().getNextEvaluationDate()).isEqualTo(state.getNextEvaluationDate());
        });
    }

    private DefaultSchedulableTriggerFetcher fetcher(InMemoryTriggerStateStore triggerStateStore, FlowWithSource flow, FlowParsingService flowParsingService) {
        return new DefaultSchedulableTriggerFetcher(
            runContextFactory,
            triggerStateStore,
            new InMemoryFlowMetaStore(1, List.of(flow)),
            flowParsingService
        );
    }

    private static FlowParsingService acceptingParser(FlowWithSource flow) throws Exception {
        FlowParsingService flowParsingService = mock(FlowParsingService.class);
        when(flowParsingService.parseForRuntime(flow)).thenReturn(ProcessedFlow.of(flow));
        return flowParsingService;
    }

    private static Blocker policyBlock() {
        return Blocker.from(new FlowBlockedException("Log plugin is not allowed by policy", List.of("deny-log")));
    }

    /**
     * Collects the warnings delivered within the await window: logs arrive out of order, so only waiting rules out a late duplicate.
     */
    private static List<LogEntry> warnings(List<LogEntry> logs) {
        return TestsUtils.awaitLogs(logs, log -> log.getLevel() == Level.WARN, count -> false);
    }

    private List<LogEntry> listenToLogs(FlowWithSource flow) {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(log -> {
            if (flow.getId().equals(log.getFlowId())) {
                logs.add(log);
            }
        });
        return logs;
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
