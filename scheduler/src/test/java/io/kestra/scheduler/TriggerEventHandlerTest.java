package io.kestra.scheduler;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.models.executions.Execution;
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
import io.kestra.core.scheduler.events.TriggerUpdated;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.IdUtils;
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

    @BeforeEach
    void setUp() {
        triggerExecutionPublisher = new CollectorTriggerExecutionPublisher();
        triggerStateStore = new InMemoryTriggerStateStore();
        triggerId = Fixtures.triggerId();
        triggerState = TriggerState.of(triggerId, TriggerType.SCHEDULE, null, false, 0);
        executionKilledQueue = Mockito.mock(BroadcastQueueInterface.class);
    }

    TriggerEventHandler newTriggerEventHandler(List<FlowWithSource> flows) {
        return new TriggerEventHandler(
            triggerStateStore,
            new InMemoryFlowMetaStore(TEST_VNODE_COUNT, flows),
            triggerExecutionPublisher,
            runContextFactory,
            conditionService,
            executionKilledQueue
        );
    }

    @Test
    void shouldCreateTriggerGivenTriggerCreatedEventWhenFlowDoesExist() {
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
            triggerId, Execution.builder()
                .id(IdUtils.create())
                .state(new State())
                .build()
        );

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(1);
    }

    @Test
    void shouldBackfillTriggerGivenValidFlowAndTriggerWhenHandled() {
        // GIVEN
        triggerStateStore.save(triggerState);
        handler = newTriggerEventHandler(List.of(Fixtures.defaultFlow()));
        CreateBackfillTrigger event = new CreateBackfillTrigger(triggerId, new CreateBackfillTrigger.Backfill(ZonedDateTime.now(), ZonedDateTime.now(), null, null));

        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);

        // THEN
        Optional<TriggerState> updated = triggerStateStore.findById(triggerId);
        assertThat(updated).isPresent();
        assertThat(updated.get().getBackfill()).isNotNull();
        assertThat(updated.get().getLastEventId()).isEqualTo(event.eventId());
    }

    @Test
    void shouldLogWarningGivenFlowNotFoundWhenBackfillHandled() {
        // GIVEN
        handler = newTriggerEventHandler(List.of());
        triggerStateStore.save(triggerState);
        CreateBackfillTrigger event = new CreateBackfillTrigger(triggerId, new CreateBackfillTrigger.Backfill(ZonedDateTime.now(), ZonedDateTime.now(), null, null));

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
}