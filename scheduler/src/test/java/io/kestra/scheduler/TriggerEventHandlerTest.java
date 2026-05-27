package io.kestra.scheduler;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.async.AsyncOperationService;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Backfill;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.scheduler.SchedulerClock;
import io.kestra.core.scheduler.events.CreateBackfillTrigger;
import io.kestra.core.scheduler.events.DeleteBackfillTrigger;
import io.kestra.core.scheduler.events.ResetTrigger;
import io.kestra.core.scheduler.events.SetDisableTrigger;
import io.kestra.core.scheduler.events.SetPauseBackfillTrigger;
import io.kestra.core.scheduler.events.TriggerCreated;
import io.kestra.core.scheduler.events.TriggerDeleted;
import io.kestra.core.scheduler.events.TriggerEvaluated;
import io.kestra.core.scheduler.events.TriggerExecutionTerminated;
import io.kestra.core.scheduler.events.TriggerFlowRevisionUpdated;
import io.kestra.core.scheduler.events.TriggerUpdated;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.scheduler.store.TriggerStateStore;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.scheduler.utils.CollectorTriggerExecutionPublisher;
import io.kestra.scheduler.utils.InMemoryFlowMetaStore;
import io.kestra.scheduler.utils.InMemoryTriggerStateStore;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@MicronautTest
class TriggerEventHandlerTest {

    private static final Clock CLOCK = SchedulerClock.getClock();
    private static final int TEST_VNODE = 1;
    private static final int TEST_VNODE_COUNT = 1;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private ConditionService conditionService;

    private TriggerEventHandler handler;

    private TriggerId triggerId;
    private TriggerState triggerState;

    private InMemoryTriggerStateStore triggerStateStore;
    private CollectorTriggerExecutionPublisher triggerExecutionPublisher;
    private BroadcastQueueInterface<ExecutionKilled> executionKilledQueue;
    private BroadcastQueueInterface<AsyncOperationProcessedEvent> asyncOperationProcessedEventQueue;

    @BeforeEach
    void setUp() {
        triggerExecutionPublisher = new CollectorTriggerExecutionPublisher();
        triggerStateStore = new InMemoryTriggerStateStore();
        triggerId = Fixtures.triggerId();
        triggerState = TriggerState.of(triggerId, TriggerType.SCHEDULE, null, false, 0);
        executionKilledQueue = Mockito.mock(BroadcastQueueInterface.class);
        asyncOperationProcessedEventQueue = Mockito.mock(BroadcastQueueInterface.class);
    }

    @AfterEach
    void tearDown() {
        SchedulerClock.setClock(Clock.systemDefaultZone());
    }

    TriggerEventHandler newTriggerEventHandler(List<FlowWithSource> flows) {
        return new TriggerEventHandler(
            triggerStateStore,
            new InMemoryFlowMetaStore(TEST_VNODE_COUNT, flows),
            triggerExecutionPublisher,
            runContextFactory,
            conditionService,
            executionKilledQueue,
            new AsyncOperationService(asyncOperationProcessedEventQueue)
        );
    }

    @Test
    void shouldCreateTriggerWithNextEvaluationDateGivenTriggerCreatedEventWhenFlowDoesExist() {
        // GIVEN
        handler = newTriggerEventHandler(List.of(Fixtures.defaultFlow()));
        TriggerCreated event = new TriggerCreated(triggerId, 1);

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> saved = triggerStateStore.findById(triggerId);
        assertThat(saved).isPresent();
        assertThat(TriggerId.of(saved.get())).isEqualTo(triggerId);
        assertThat(saved.get().getLastEventId()).isNotNull();
        assertThat(saved.get().getNextEvaluationDate()).isNotNull();
    }

    @Test
    void shouldNotCreateTriggerGivenTriggerCreatedEventWhenFlowDoesNotExist() {
        // GIVEN
        handler = newTriggerEventHandler(List.of());
        TriggerCreated event = new TriggerCreated(triggerId, 1);

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> saved = triggerStateStore.findById(triggerId);
        assertThat(saved).isEmpty();
    }

    @Test
    void shouldDeleteTriggerGivenTriggerDeletedEventWhenHandled() throws QueueException {
        // GIVEN
        triggerStateStore.save(triggerState);
        handler = newTriggerEventHandler(List.of());
        TriggerDeleted event = new TriggerDeleted(triggerId);

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        assertThat(triggerStateStore.findById(triggerId)).isEmpty();
        Mockito.verify(executionKilledQueue, Mockito.never()).emit(Mockito.any(ExecutionKilled.class));
    }

    @Test
    void shouldSendKillGivenTriggerDeletedEventForRealTimeTriggerWhenHandled() throws QueueException {
        // GIVEN
        triggerStateStore.save(TriggerState.of(triggerId, TriggerType.REALTIME, null, false, 0));
        handler = newTriggerEventHandler(List.of());
        TriggerDeleted event = new TriggerDeleted(triggerId);

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        assertThat(triggerStateStore.findById(triggerId)).isEmpty();
        Mockito.verify(executionKilledQueue, Mockito.only()).emit(Mockito.any(ExecutionKilled.class));
    }

    @Test
    void shouldUpdateTriggerGivenExistingFlowWhenTriggerUpdated() {
        // GIVEN
        triggerStateStore.save(triggerState);
        handler = newTriggerEventHandler(
            List.of(
                Fixtures.defaultFlow(
                    build -> build.disabled(true).build()
                )
            )
        );
        TriggerUpdated event = new TriggerUpdated(triggerId, Fixtures.defaultFlow().getRevision());

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> updated = triggerStateStore.findById(triggerId);
        assertThat(updated).isPresent();
        assertThat(updated.get().isDisabled()).isTrue();
        assertThat(updated.get().getUpdatedAt()).isAfter(triggerState.getUpdatedAt());
        assertThat(updated.get().getLastEventId()).isEqualTo(event.eventId());
        assertThat(updated.get().getNextEvaluationDate()).isNotNull();
    }

    @Test
    void shouldRecomputeNextEvaluationDateWhenTriggerUpdated() {
        // GIVEN
        ZonedDateTime staleNextEvaluationDate = SchedulerClock.now().minusMinutes(30);
        triggerStateStore.save(triggerState.updateForNextEvaluationDate(CLOCK, staleNextEvaluationDate));
        handler = newTriggerEventHandler(List.of(Fixtures.defaultFlow()));
        TriggerUpdated event = new TriggerUpdated(triggerId, Fixtures.defaultFlow().getRevision());

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> updated = triggerStateStore.findById(triggerId);
        assertThat(updated).isPresent();
        assertThat(updated.get().getNextEvaluationDate()).isAfter(staleNextEvaluationDate.toInstant());
        assertThat(updated.get().getLastEventId()).isEqualTo(event.eventId());
    }

    @Test
    void shouldNotMutateTriggerStateWhenFlowRevisionUpdated() {
        // GIVEN
        ZonedDateTime initialNextEvaluationDate = SchedulerClock.now().plusMinutes(5);
        TriggerState initial = triggerState.updateForNextEvaluationDate(CLOCK, initialNextEvaluationDate);
        triggerStateStore.save(initial);
        handler = newTriggerEventHandler(List.of(Fixtures.defaultFlow()));
        TriggerFlowRevisionUpdated event = new TriggerFlowRevisionUpdated(triggerId, Fixtures.defaultFlow().getRevision());

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> after = triggerStateStore.findById(triggerId);
        assertThat(after).isPresent();
        assertThat(after.get().getNextEvaluationDate()).isEqualTo(initialNextEvaluationDate.toInstant());
        assertThat(after.get().getUpdatedAt()).isEqualTo(initial.getUpdatedAt());
        assertThat(after.get().getLastEventId()).isEqualTo(initial.getLastEventId());
    }

    @Test
    void shouldResetTriggerGivenExistingStateWhenResetEventHandled() {
        // GIVEN
        triggerStateStore.save(triggerState.locked(Clock.systemDefaultZone(), true));
        handler = newTriggerEventHandler(List.of());
        ResetTrigger event = new ResetTrigger(triggerId);

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> updated = triggerStateStore.findById(triggerId);
        assertThat(updated).isPresent();
        assertThat(updated.get().isLocked()).isFalse();
        assertThat(updated.get().getUpdatedAt()).isAfter(triggerState.getUpdatedAt());
        assertThat(updated.get().getLastEventId()).isEqualTo(event.eventId());
    }

    @Test
    void shouldResetTriggerAndRecomputeNextEvaluationDateWhenFlowExists() {
        // GIVEN
        triggerStateStore.save(triggerState
            .locked(Clock.systemDefaultZone(), true)
            .updateForNextEvaluationDate(CLOCK, SchedulerClock.now().minusMinutes(15)));
        handler = newTriggerEventHandler(List.of(Fixtures.defaultFlow()));
        ResetTrigger event = new ResetTrigger(triggerId);

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> updated = triggerStateStore.findById(triggerId);
        assertThat(updated).isPresent();
        assertThat(updated.get().isLocked()).isFalse();
        assertThat(updated.get().getNextEvaluationDate()).isNotNull();
        assertThat(updated.get().getNextEvaluationDate()).isAfter(Instant.now().minusSeconds(1));
        assertThat(updated.get().getLastEventId()).isEqualTo(event.eventId());
    }

    @Test
    void shouldDisableTriggerGivenDisableEventWhenHandled() {
        // GIVEN
        triggerStateStore.save(triggerState);
        handler = newTriggerEventHandler(List.of());
        SetDisableTrigger event = new SetDisableTrigger(triggerId, true);

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> updated = triggerStateStore.findById(triggerId);
        assertThat(updated).isPresent();
        assertThat(updated.get().isDisabled()).isTrue();
        assertThat(updated.get().getUpdatedAt()).isAfter(triggerState.getUpdatedAt());
        assertThat(updated.get().getLastEventId()).isEqualTo(event.eventId());
    }

    @Test
    void shouldUpdateNextEvaluationDateWhenReEnablingTrigger() {
        // GIVEN
        ZonedDateTime initialNextEvaluationDate = SchedulerClock.now().minusMinutes(30);
        triggerStateStore.save(triggerState.updateForNextEvaluationDate(CLOCK, initialNextEvaluationDate).disabled(CLOCK, true));

        handler = newTriggerEventHandler(List.of(Fixtures.defaultFlow()));
        SetDisableTrigger event = new SetDisableTrigger(triggerId, false);

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> updated = triggerStateStore.findById(triggerId);
        assertThat(updated).isPresent();
        assertThat(updated.get().isDisabled()).isFalse();
        assertThat(updated.get().getUpdatedAt()).isAfter(triggerState.getUpdatedAt());
        assertThat(updated.get().getNextEvaluationDate()).isAfter(initialNextEvaluationDate.toInstant());
        assertThat(updated.get().getLastEventId()).isEqualTo(event.eventId());
    }

    @Test
    void shouldPauseBackfillGivenBackfillExistsWhenPauseBackfillEventHandled() {
        // GIVEN
        Backfill backfill = Backfill.builder()
            .start(ZonedDateTime.now(CLOCK))
            .end(ZonedDateTime.now(CLOCK))
            .paused(false)
            .build();
        triggerStateStore.save(triggerState.backfill(CLOCK, backfill));
        handler = newTriggerEventHandler(List.of());
        SetPauseBackfillTrigger event = new SetPauseBackfillTrigger(triggerId, true);

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> updated = triggerStateStore.findById(triggerId);
        assertThat(updated)
            .get()
            .extracting(t -> t.getBackfill().getPaused())
            .isEqualTo(true);
        assertThat(updated.get().getLastEventId()).isEqualTo(event.eventId());
    }

    @Test
    void shouldResumeBackfillGivenBackfillExistsWhenPauseBackfillEventHandled() {
        // GIVEN
        Backfill backfill = Backfill.builder()
            .start(ZonedDateTime.now(CLOCK))
            .end(ZonedDateTime.now(CLOCK))
            .paused(true)
            .build();
        triggerStateStore.save(triggerState.backfill(CLOCK, backfill));
        handler = newTriggerEventHandler(List.of());
        SetPauseBackfillTrigger event = new SetPauseBackfillTrigger(triggerId, false);

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> updated = triggerStateStore.findById(triggerId);
        assertThat(updated)
            .get()
            .extracting(t -> t.getBackfill().getPaused())
            .isEqualTo(false);
        assertThat(updated.get().getLastEventId()).isEqualTo(event.eventId());
    }

    @Test
    void shouldPreserveCurrentDateGivenAdvancedBackfillWhenPaused() {
        // GIVEN: a backfill that has progressed past its start date
        ZonedDateTime start = ZonedDateTime.now(CLOCK).minusDays(10);
        ZonedDateTime end = ZonedDateTime.now(CLOCK);
        ZonedDateTime advanced = start.plusDays(4);
        Backfill backfill = Backfill.builder()
            .start(start)
            .end(end)
            .currentDate(advanced)
            .paused(false)
            .build();
        triggerStateStore.save(triggerState.backfill(CLOCK, backfill));
        handler = newTriggerEventHandler(List.of());
        SetPauseBackfillTrigger event = new SetPauseBackfillTrigger(triggerId, true);

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN: currentDate is preserved (progress bar must not reset)
        Optional<TriggerState> updated = triggerStateStore.findById(triggerId);
        assertThat(updated).get()
            .extracting(t -> t.getBackfill().getCurrentDate())
            .isEqualTo(advanced);
    }

    @Test
    void shouldCompleteTriggerGivenTriggerCompletedEventWhenHandled() {
        // GIVEN
        triggerStateStore.save(triggerState);
        handler = newTriggerEventHandler(List.of());
        TriggerExecutionTerminated event = new TriggerExecutionTerminated(triggerId, IdUtils.create(), State.Type.SUCCESS);

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> updated = triggerStateStore.findById(triggerId);
        assertThat(updated).isPresent();
        assertThat(updated.get().getLastEventId()).isEqualTo(event.eventId());
    }

    @Test
    void shouldExecuteTriggerGivenFlowAndExecutionWhenHandled() {
        // GIVEN
        triggerStateStore.save(triggerState);
        handler = newTriggerEventHandler(List.of(Fixtures.defaultFlow()));
        TriggerEvaluated event = new TriggerEvaluated(
            triggerId, new TriggerEvaluationResult(
                IdUtils.create(),
                State.Type.CREATED,
                null,
                null,
                null,
                null,
                null
            )
        );

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(1);
    }

    @Test
    void shouldExecuteFailedTriggerGivenFlowAndFailedEvaluationWhenHandled() {
        // GIVEN
        triggerStateStore.save(triggerState);
        handler = newTriggerEventHandler(List.of(Fixtures.defaultFlow()));
        TriggerEvaluated event = new TriggerEvaluated(
            triggerId, new TriggerEvaluationResult(
                IdUtils.create(),
                State.Type.FAILED,
                null,
                null,
                null,
                null,
                null
            )
        );

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(1);
        assertThat(triggerExecutionPublisher.executions().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    void shouldNotPublishExecutionGivenNullEvaluationWhenHandled() {
        // GIVEN
        triggerStateStore.save(triggerState);
        handler = newTriggerEventHandler(List.of(Fixtures.defaultFlow()));
        TriggerEvaluated event = new TriggerEvaluated(triggerId, null);

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(0);
    }

    @Test
    void shouldBackfillTriggerGivenValidFlowAndTriggerWhenHandled() {
        // GIVEN
        triggerStateStore.save(triggerState);
        handler = newTriggerEventHandler(List.of(Fixtures.defaultFlow()));
        ZonedDateTime backfillStart = ZonedDateTime.now(CLOCK).minusDays(1);
        ZonedDateTime backfillEnd = ZonedDateTime.now(CLOCK).plusDays(1);
        CreateBackfillTrigger event = new CreateBackfillTrigger(triggerId, new CreateBackfillTrigger.Backfill(backfillStart, backfillEnd, null, null));

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> updated = triggerStateStore.findById(triggerId);
        assertThat(updated).isPresent();
        assertThat(updated.get().getBackfill()).isNotNull();
        assertThat(updated.get().getBackfill().getStart()).isEqualTo(backfillStart);
        assertThat(updated.get().getBackfill().getEnd()).isEqualTo(backfillEnd);
        assertThat(updated.get().getNextEvaluationDate()).isNotNull();
        assertThat(updated.get().getNextEvaluationDate()).isAfter(backfillStart.toInstant());
        assertThat(updated.get().getLastEventId()).isEqualTo(event.eventId());
    }

    @Test
    void shouldClearBackfillWhenBackfillRangeIsAlreadyComplete() {
        // GIVEN
        triggerStateStore.save(triggerState);
        handler = newTriggerEventHandler(List.of(Fixtures.defaultFlow()));
        // Backfill with start == end == now: the next cron tick is after end, so backfill completes immediately
        ZonedDateTime now = ZonedDateTime.now(CLOCK);
        CreateBackfillTrigger event = new CreateBackfillTrigger(triggerId, new CreateBackfillTrigger.Backfill(now, now, null, null));

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> updated = triggerStateStore.findById(triggerId);
        assertThat(updated).isPresent();
        // Backfill is cleared because the next evaluation date is after the backfill end
        assertThat(updated.get().getBackfill()).isNull();
        assertThat(updated.get().getNextEvaluationDate()).isNotNull();
        assertThat(updated.get().getLastEventId()).isEqualTo(event.eventId());
    }

    @Test
    void shouldLogWarningGivenFlowNotFoundWhenBackfillHandled() {
        // GIVEN
        handler = newTriggerEventHandler(List.of());
        triggerStateStore.save(triggerState);
        CreateBackfillTrigger event = new CreateBackfillTrigger(triggerId, new CreateBackfillTrigger.Backfill(ZonedDateTime.now(CLOCK), ZonedDateTime.now(CLOCK), null, null));

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        // no exception expected, handled gracefully
        assertThat(triggerStateStore.findById(triggerId)).isPresent();
    }

    @Test
    void shouldNotUpdateGivenTriggerEventTwice() {
        // GIVEN
        triggerStateStore.save(triggerState);
        handler = newTriggerEventHandler(List.of(Fixtures.defaultFlow()));
        TriggerUpdated event = new TriggerUpdated(triggerId, Fixtures.defaultFlow().getRevision());

        // WHEN (first)
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        TriggerState updated;

        updated = triggerStateStore.findById(triggerId).orElseThrow();
        assertThat(updated.getLastEventId()).isEqualTo(event.eventId());
        Instant updatedAt = updated.getUpdatedAt();
        assertThat(updatedAt).isAfter(triggerState.getUpdatedAt());

        // WHEN (second)
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        updated = triggerStateStore.findById(triggerId).orElseThrow();
        assertThat(updated.getLastEventId()).isEqualTo(event.eventId());
        assertThat(updated.getUpdatedAt()).isEqualTo(updatedAt); // not updated
    }

    @Test
    void shouldNotUpdateGivenOldTriggerEvent() {
        // GIVEN
        TriggerUpdated event1 = new TriggerUpdated(triggerId, Fixtures.defaultFlow().getRevision());
        TriggerUpdated event2 = new TriggerUpdated(triggerId, Fixtures.defaultFlow().getRevision());

        TriggerState state = triggerState.lastEventId(CLOCK, event2.eventId());
        triggerStateStore.save(state);
        handler = newTriggerEventHandler(List.of(Fixtures.defaultFlow()));

        // WHEN (first)
        handler.handle(CLOCK, TEST_VNODE, event1);

        // THEN
        TriggerState updated;

        updated = triggerStateStore.findById(triggerId).orElseThrow();
        assertThat(updated.getLastEventId()).isEqualTo(event2.eventId());
        assertThat(updated.getUpdatedAt()).isEqualTo(state.getUpdatedAt()); // not updated
    }

    @Test
    void shouldDeleteBackfillAndResetNextEvaluationDateWhenDeleteBackfillEventHandled() {
        // GIVEN
        Backfill backfill = Backfill.builder()
            .start(SchedulerClock.now())
            .end(SchedulerClock.now())
            .paused(false)
            .build();
        ZonedDateTime previousNextEvaluationDate = SchedulerClock.now().minusMinutes(1);
        triggerStateStore.save(triggerState.updateForNextEvaluationDate(CLOCK, previousNextEvaluationDate).backfill(CLOCK, backfill));
        handler = newTriggerEventHandler(List.of());
        DeleteBackfillTrigger event = new DeleteBackfillTrigger(triggerId);

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> updated = triggerStateStore.findById(triggerId);
        assertThat(updated).get().extracting(TriggerState::getBackfill).isNull();
        assertThat(updated).get().extracting(TriggerState::getNextEvaluationDate).isEqualTo(previousNextEvaluationDate.toInstant());
        assertThat(updated).get().extracting(TriggerState::getLastEventId).isEqualTo(event.eventId());
    }

    // Fixed clock on Wednesday 2024-01-03 at 10:00 in the system default zone.
    // With cron "*/1 * * * *" + DayWeek=SUNDAY condition, the next matching tick is the next
    // Sunday 00:00 in the Schedule's timezone (system default). The exact instant depends on
    // the system zone, so the tests assert on day-of-week rather than a hard-coded instant.
    private static final ZonedDateTime FIXED_WEDNESDAY = LocalDateTime.of(2024, 1, 3, 10, 0)
        .atZone(ZoneId.systemDefault());

    private Clock fixWedClock() {
        Clock fixed = Clock.fixed(FIXED_WEDNESDAY.toInstant(), ZoneId.systemDefault());
        SchedulerClock.setClock(fixed);
        return fixed;
    }

    private void assertMatchesNextSunday(TriggerState state) {
        assertThat(state.getNextEvaluationDate()).isNotNull();
        ZonedDateTime nextZoned = state.getNextEvaluationDate().atZone(ZoneId.systemDefault());
        assertThat(nextZoned.getDayOfWeek())
            .as("nextEvaluationDate should fall on a SUNDAY in the schedule's timezone, but was %s", nextZoned)
            .isEqualTo(DayOfWeek.SUNDAY);
        assertThat(nextZoned).isAfter(FIXED_WEDNESDAY);
    }

    @Test
    void shouldComputeNextEvaluationDateRespectingConditionsWhenTriggerCreated() {
        // GIVEN a brand-new trigger (evaluatedAt=null) with DayWeek=SUNDAY condition, clock on Friday
        Clock clock = fixWedClock();
        handler = newTriggerEventHandler(List.of(Fixtures.flowWithEveryMinuteScheduleOnDayWeek(ZoneId.systemDefault().getId(), DayOfWeek.SUNDAY)));
        TriggerCreated event = new TriggerCreated(triggerId, 0);

        // WHEN
        handler.handle(clock, TEST_VNODE, event);

        // THEN persisted nextEvaluationDate falls on SUNDAY, not the next raw cron tick
        Optional<TriggerState> saved = triggerStateStore.findById(triggerId);
        assertThat(saved).isPresent();
        assertMatchesNextSunday(saved.get());
    }

    @Test
    void shouldRecomputeNextEvaluationDateRespectingConditionsWhenTriggerUpdated() {
        // GIVEN a trigger evaluated once before the update event fires
        Clock clock = fixWedClock();
        triggerStateStore.save(triggerState
            .evaluatedAt(clock, FIXED_WEDNESDAY)
            .updateForNextEvaluationDate(clock, FIXED_WEDNESDAY.plusMinutes(1)));
        FlowWithSource flow = Fixtures.flowWithEveryMinuteScheduleOnDayWeek(ZoneId.systemDefault().getId(), DayOfWeek.SUNDAY);
        handler = newTriggerEventHandler(List.of(flow));
        TriggerUpdated event = new TriggerUpdated(triggerId, flow.getRevision());

        // WHEN
        handler.handle(clock, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> updated = triggerStateStore.findById(triggerId);
        assertThat(updated).isPresent();
        assertMatchesNextSunday(updated.get());
    }

    @Test
    void shouldRecomputeNextEvaluationDateRespectingConditionsWhenTriggerReset() {
        // GIVEN
        Clock clock = fixWedClock();
        triggerStateStore.save(triggerState
            .evaluatedAt(clock, FIXED_WEDNESDAY)
            .updateForNextEvaluationDate(clock, FIXED_WEDNESDAY.plusMinutes(1)));
        handler = newTriggerEventHandler(List.of(Fixtures.flowWithEveryMinuteScheduleOnDayWeek(ZoneId.systemDefault().getId(), DayOfWeek.SUNDAY)));
        ResetTrigger event = new ResetTrigger(triggerId);

        // WHEN
        handler.handle(clock, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> updated = triggerStateStore.findById(triggerId);
        assertThat(updated).isPresent();
        assertMatchesNextSunday(updated.get());
    }

    @Test
    void shouldRecomputeNextEvaluationDateRespectingConditionsWhenTriggerReEnabled() {
        // GIVEN a trigger evaluated once before being disabled and re-enabled
        Clock clock = fixWedClock();
        triggerStateStore.save(triggerState
            .evaluatedAt(clock, FIXED_WEDNESDAY)
            .updateForNextEvaluationDate(clock, FIXED_WEDNESDAY.plusMinutes(1))
            .disabled(clock, true));
        handler = newTriggerEventHandler(List.of(Fixtures.flowWithEveryMinuteScheduleOnDayWeek(ZoneId.systemDefault().getId(), DayOfWeek.SUNDAY)));
        SetDisableTrigger event = new SetDisableTrigger(triggerId, false);

        // WHEN
        handler.handle(clock, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> updated = triggerStateStore.findById(triggerId);
        assertThat(updated).isPresent();
        assertThat(updated.get().isDisabled()).isFalse();
        assertMatchesNextSunday(updated.get());
    }

    @Test
    void shouldEmitSucceededProcessedEventWhenCommandCarriesOperationId() throws QueueException {
        // GIVEN: a stored trigger state and a SetDisableTrigger carrying an operationId
        triggerStateStore.save(triggerState);
        handler = newTriggerEventHandler(List.of());
        String operationId = IdUtils.create();
        SetDisableTrigger event = new SetDisableTrigger(triggerId, true).withOperationId(operationId);

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN: a SUCCEEDED processed event is emitted on the async operation queue.
        ArgumentCaptor<AsyncOperationProcessedEvent> captor = ArgumentCaptor.forClass(AsyncOperationProcessedEvent.class);
        Mockito.verify(asyncOperationProcessedEventQueue).emit(captor.capture());
        AsyncOperationProcessedEvent emitted = captor.getValue();
        assertThat(emitted.operationId()).isEqualTo(operationId);
        assertThat(emitted.tenantId()).isEqualTo(triggerId.getTenantId());
        assertThat(emitted.itemId()).isEqualTo(event.uid());
        assertThat(emitted.outcome()).isEqualTo(AsyncOperationProcessedEvent.Outcome.SUCCEEDED);
        assertThat(emitted.error()).isNull();
    }

    @Test
    void shouldNotEmitProcessedEventWhenCommandHasNoOperationId() throws QueueException {
        // GIVEN: a stored trigger state and a SetDisableTrigger WITHOUT operationId
        triggerStateStore.save(triggerState);
        handler = newTriggerEventHandler(List.of());
        SetDisableTrigger event = new SetDisableTrigger(triggerId, true);

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN: the async operation queue is never touched.
        Mockito.verify(asyncOperationProcessedEventQueue, Mockito.never()).emit(Mockito.any(AsyncOperationProcessedEvent.class));
    }

    @Test
    void shouldEmitFailedProcessedEventWhenHandlerThrows() throws QueueException {
        // GIVEN: a TriggerStateStore that throws on findById to force a RuntimeException inside doHandle
        TriggerStateStore failingStore = Mockito.mock(TriggerStateStore.class);
        Mockito.when(failingStore.findById(Mockito.any())).thenThrow(new RuntimeException("boom"));
        handler = new TriggerEventHandler(
            failingStore,
            new InMemoryFlowMetaStore(TEST_VNODE_COUNT, List.of()),
            triggerExecutionPublisher,
            runContextFactory,
            conditionService,
            executionKilledQueue,
            new AsyncOperationService(asyncOperationProcessedEventQueue)
        );
        String operationId = IdUtils.create();
        SetDisableTrigger event = new SetDisableTrigger(triggerId, true).withOperationId(operationId);

        // WHEN / THEN
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.handle(CLOCK, TEST_VNODE, event))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("boom");

        // AND: a FAILED processed event is emitted with the error message.
        ArgumentCaptor<AsyncOperationProcessedEvent> captor = ArgumentCaptor.forClass(AsyncOperationProcessedEvent.class);
        Mockito.verify(asyncOperationProcessedEventQueue).emit(captor.capture());
        AsyncOperationProcessedEvent emitted = captor.getValue();
        assertThat(emitted.operationId()).isEqualTo(operationId);
        assertThat(emitted.itemId()).isEqualTo(event.uid());
        assertThat(emitted.outcome()).isEqualTo(AsyncOperationProcessedEvent.Outcome.FAILED);
        assertThat(emitted.error()).isEqualTo("boom");
    }
}