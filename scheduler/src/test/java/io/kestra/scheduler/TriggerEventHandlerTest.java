package io.kestra.scheduler;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Backfill;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.LogService;
import io.kestra.core.utils.IdUtils;
import io.kestra.scheduler.events.CreateBackfillTrigger;
import io.kestra.scheduler.events.ResetTrigger;
import io.kestra.scheduler.events.SetDisableTrigger;
import io.kestra.scheduler.events.SetPauseBackfillTrigger;
import io.kestra.scheduler.events.TriggerExecutionTerminated;
import io.kestra.scheduler.events.TriggerCreated;
import io.kestra.scheduler.events.TriggerDeleted;
import io.kestra.scheduler.events.TriggerEvaluated;
import io.kestra.scheduler.events.TriggerUpdated;
import io.kestra.scheduler.model.TriggerState;
import io.kestra.scheduler.utils.InMemoryFlowMetaStore;
import io.kestra.scheduler.utils.InMemoryTriggerStateStore;
import io.kestra.scheduler.utils.CollectorTriggerExecutionPublisher;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@KestraTest
class TriggerEventHandlerTest {
    
    private static final Clock CLOCK = SchedulerClock.getClock();
    private static final int TEST_VNODE = 1;
    private static final int TEST_VNODE_COUNT = 1;
    
    @Inject
    private RunContextFactory runContextFactory;
    
    @Inject
    private ConditionService conditionService;
    
    @Inject
    private LogService logService;
    
    private TriggerEventHandler handler;
    
    private TriggerId triggerId;
    private TriggerState triggerState;
    
    private InMemoryTriggerStateStore triggerStateStore;
    private CollectorTriggerExecutionPublisher triggerExecutionPublisher;
    
    @BeforeEach
    void setUp() {
        triggerExecutionPublisher = new CollectorTriggerExecutionPublisher();
        triggerStateStore = new InMemoryTriggerStateStore();
        triggerId = Fixtures.triggerId();
        triggerState = TriggerState.of(triggerId, null, false, 0);
    }
    
    TriggerEventHandler newTriggerEventHandler(List<FlowWithSource> flows) {
        return new TriggerEventHandler(
            triggerStateStore,
            new InMemoryFlowMetaStore(TEST_VNODE_COUNT, flows),
            triggerExecutionPublisher,
            runContextFactory,
            conditionService,
            logService
        );
    }
    
    @Test
    void shouldCreateTriggerGivenTriggerCreatedEventWhenFlowDoesExist() {
        // GIVEN
        handler = newTriggerEventHandler(List.of(Fixtures.defaultFlow()));
        TriggerCreated event = new TriggerCreated(triggerId,  1);
        
        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);
        
        // THEN
        Optional<TriggerState> saved = triggerStateStore.find(triggerId);
        assertThat(saved).isPresent();
        assertThat(TriggerId.of(saved.get())).isEqualTo(triggerId);
    }
    
    @Test
    void shouldNotCreateTriggerGivenTriggerCreatedEventWhenFlowDoesNotExist() {
        // GIVEN
        handler = newTriggerEventHandler(List.of());
        TriggerCreated event = new TriggerCreated(triggerId,  1);
        
        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);
        
        // THEN
        Optional<TriggerState> saved = triggerStateStore.find(triggerId);
        assertThat(saved).isEmpty();
    }
    
    @Test
    void shouldDeleteTriggerGivenTriggerDeletedEventWhenHandled() {
        // GIVEN
        triggerStateStore.save(triggerState);
        handler = newTriggerEventHandler(List.of());
        TriggerDeleted event = new TriggerDeleted(triggerId);
        
        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);
        
        // THEN
        assertThat(triggerStateStore.find(triggerId)).isEmpty();
    }
    
    @Test
    void shouldUpdateTriggerGivenExistingFlowWhenTriggerUpdated() {
        // GIVEN
        triggerStateStore.save(triggerState);
        handler = newTriggerEventHandler(List.of(Fixtures.defaultFlow(
                build -> build.disabled(true).build())
            )
        );
        TriggerUpdated event = new TriggerUpdated(triggerId, Fixtures.defaultFlow().getRevision());
        
        // WHEN
        handler.handle(CLOCK, TEST_VNODE, event);
        
        // THEN
        Optional<TriggerState> updated = triggerStateStore.find(triggerId);
        assertThat(updated).isPresent();
        assertThat(updated.get().getDisabled()).isTrue();
        assertThat(updated.get().getUpdatedAt()).isNotEqualTo(triggerState.getUpdatedAt());
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
        Optional<TriggerState> updated = triggerStateStore.find(triggerId);
        assertThat(updated).isPresent();
        assertThat(updated.get().getLocked()).isFalse();
        assertThat(updated.get().getUpdatedAt()).isNotEqualTo(triggerState.getUpdatedAt());
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
        Optional<TriggerState> updated = triggerStateStore.find(triggerId);
        assertThat(updated).isPresent();
        assertThat(updated.get().getDisabled()).isTrue();
        assertThat(updated.get().getUpdatedAt()).isNotEqualTo(triggerState.getUpdatedAt());
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
        assertThat(triggerStateStore.find(triggerId))
            .get()
            .extracting(t -> t.getBackfill().getPaused())
            .isEqualTo(true);
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
        assertThat(triggerStateStore.find(triggerId))
            .get()
            .extracting(t -> t.getBackfill().getPaused())
            .isEqualTo(false);
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
        assertThat(triggerStateStore.find(triggerId)).isPresent();
    }
    
    @Test
    void shouldExecuteTriggerGivenFlowAndExecutionWhenHandled() {
        // GIVEN
        triggerStateStore.save(triggerState);
        handler = newTriggerEventHandler(List.of(Fixtures.defaultFlow()));
        TriggerEvaluated event = new TriggerEvaluated(triggerId, Execution.builder()
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
        Optional<TriggerState> updated = triggerStateStore.find(triggerId);
        assertThat(updated).isPresent();
        assertThat(updated.get().getBackfill()).isNotNull();
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
        assertThat(triggerStateStore.find(triggerId)).isPresent();
    }
}