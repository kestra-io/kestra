package io.kestra.scheduler;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Backfill;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.models.triggers.RecoverMissedSchedules;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.scheduler.SchedulerClock;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.plugin.core.condition.DayWeekInMonth;
import io.kestra.scheduler.internals.DefaultSchedulableTriggerFetcher;
import io.kestra.scheduler.internals.SchedulableEvaluator;
import io.kestra.scheduler.pubsub.TriggerWorkerJobPublisher;
import io.kestra.scheduler.utils.CollectorTriggerExecutionPublisher;
import io.kestra.scheduler.utils.InMemoryFlowMetaStore;
import io.kestra.scheduler.utils.InMemoryTriggerStateStore;
import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@KestraTest
class TriggerSchedulerTest {

    private static final Set<Integer> NODES_ASSIGNMENTS = Set.of(0);
    private static final String TEST_TZ = "Europe/Paris";

    @Inject
    MetricRegistry metricRegistry;

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    ConditionService conditionService;

    @Inject
    PluginDefaultService pluginDefaultService;

    @Inject
    SchedulableEvaluator schedulableEvaluator;

    @Inject
    TriggerWorkerJobPublisher triggerWorkerJobPublisher;

    private Clock initialSchedulerClock;
    private InMemoryTriggerStateStore triggerStateStore;
    private CollectorTriggerExecutionPublisher triggerExecutionPublisher;

    @BeforeEach
    void beforeEach() {
        ZonedDateTime roundedHour = ZonedDateTime.now().withMinute(0).withSecond(0).withNano(0);
        initialSchedulerClock = Clock.fixed(roundedHour.toInstant(), ZoneId.systemDefault());
        SchedulerClock.setClock(initialSchedulerClock);
        triggerStateStore = new InMemoryTriggerStateStore();
        triggerExecutionPublisher = new CollectorTriggerExecutionPublisher();
    }

    @Test
    void shouldCreateMissingTriggerStateOnStart() {
        // region [GIVEN]
        FlowWithSource flow = Fixtures.flowWithSchedulePT15M(TEST_TZ);
        TriggerScheduler scheduler = newTriggerScheduler(List.of(flow));
        // endregion [GIVEN]

        // WHEN
        scheduler.onStart(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS); // vNode are 0-based

        // THEN
        TriggerState state = triggerStateStore.find(Fixtures.triggerId()).orElse(null);
        assertThat(state).isNotNull();
        assertThat(state.isLocked()).isFalse();
        assertThat(state.getEvaluatedAt()).isNull();
    }

    @Test
    void shouldSucceedScheduleScheduleTriggerGivenValidTimeZone() {
        // region [GIVEN]
        FlowWithSource flow = Fixtures.flowWithSchedulePT15M(TEST_TZ);
        TriggerScheduler scheduler = newTriggerScheduler(List.of(flow));
        scheduler.onStart(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS); // vNode are 0-based
        // endregion [GIVEN]

        // WHEN
        SchedulerClock.offset(Duration.ofMinutes(15));
        scheduler.onSchedule(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);

        // THEN
        TriggerState state = triggerStateStore.find(Fixtures.triggerId()).orElse(null);
        assertThat(state).isNotNull();

        assertThat(state.isLocked()).isTrue();
        assertThat(state.getEvaluatedAt()).isEqualTo(SchedulerClock.now().toInstant());
        assertThat(state.getNextEvaluationDate()).isEqualTo(SchedulerClock.now().plusMinutes(15).toInstant());

        // Check that an execution was created
        assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(1);

        Execution execution = triggerExecutionPublisher.executions().getFirst();
        assertThat(execution.getScheduleDate()).isEqualTo(SchedulerClock.now().toInstant());
        assertThat(execution.getTenantId()).isEqualTo(flow.getTenantId());
        assertThat(execution.getNamespace()).isEqualTo(flow.getNamespace());
        assertThat(execution.getFlowId()).isEqualTo(flow.getId());
    }

    @Test
    void shouldSucceedSchedulePollingTrigger() {
        // region [GIVEN]

        FlowWithSource flow = Fixtures.flowWithTrigger(TestPollingTrigger.builder()
            .id("polling")
            .type(TestPollingTrigger.class.getName())
            .interval(Duration.ofMinutes(30))
            .build()
        );
        TriggerScheduler scheduler = newTriggerScheduler(List.of(flow));
        scheduler.onStart(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS); // vNode are 0-based
        // endregion [GIVEN]

        // WHEN
        scheduler.onSchedule(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);

        // THEN
        TriggerState state = triggerStateStore.find(Fixtures.triggerId("polling")).orElse(null);
        assertThat(state).isNotNull();

        assertThat(state.isLocked()).isTrue();
        assertThat(state.getEvaluatedAt()).isEqualTo(SchedulerClock.now().toInstant());
        assertThat(state.getNextEvaluationDate()).isEqualTo(SchedulerClock.now().plusMinutes(30).toInstant());
    }

    @Test
    void shouldNotFailWhenSchedulingPollingTriggerGivenTooLargeInterval() {
        // region [GIVEN]

        FlowWithSource flow = Fixtures.flowWithTrigger(TestPollingTrigger.builder()
            .id("polling")
            .type(TestPollingTrigger.class.getName())
            .interval(Duration.parse("PT99999999898989981M"))
            .build()
        );
        TriggerScheduler scheduler = newTriggerScheduler(List.of(flow));
        scheduler.onStart(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS); // vNode are 0-based
        // endregion [GIVEN]

        // WHEN
        scheduler.onSchedule(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);

        // THEN
        TriggerState state = triggerStateStore.find(Fixtures.triggerId("polling")).orElse(null);
        assertThat(state).isNotNull();

        assertThat(state.isLocked()).isFalse();
        assertThat(state.getEvaluatedAt()).isEqualTo(SchedulerClock.now().toInstant());
        assertThat(state.getNextEvaluationDate()).isEqualTo(SchedulerClock.now().toInstant());
    }

    @Test
    void shouldSucceedScheduleRealTimeTriggerGivenValidTimeZone() {
        // region [GIVEN]
        FlowWithSource flow = Fixtures.flowWithTrigger(TestRealTimeTrigger.builder()
            .id("realtime")
            .type(TestRealTimeTrigger.class.getName())
            .build()
        );
        TriggerScheduler scheduler = newTriggerScheduler(List.of(flow));
        scheduler.onStart(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS); // vNode are 0-based
        // endregion [GIVEN]

        // WHEN
        scheduler.onSchedule(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);

        // THEN
        TriggerState state = triggerStateStore.find(Fixtures.triggerId("realtime")).orElse(null);
        assertThat(state).isNotNull();

        assertThat(state.isLocked()).isTrue();
        assertThat(state.getEvaluatedAt()).isEqualTo(SchedulerClock.now().toInstant());
        assertThat(state.getNextEvaluationDate()).isEqualTo(SchedulerClock.now().toInstant());
    }

    @Test
    void shouldSucceedScheduleScheduleOnDateTriggerGivenValidTimeZone() {
        // region [GIVEN]
        FlowWithSource flow = Fixtures.flowWithScheduleOnDate(TEST_TZ, List.of(
            ZonedDateTime.parse("2025-11-02T00:00:00Z"),
            ZonedDateTime.parse("2025-11-04T00:00:00Z")
        ));
        TriggerScheduler scheduler = newTriggerScheduler(List.of(flow));
        scheduler.onStart(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS); // vNode are 0-based
        // endregion [GIVEN]

        // WHEN
        SchedulerClock.setClock(Clock.fixed(Instant.parse("2025-11-01T00:00:00Z"), ZoneId.systemDefault()));
        scheduler.onSchedule(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);

        // THEN - still not on first date => no execution
        assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(0);

        // WHEN - move forward to the first date
        SchedulerClock.setClock(Clock.fixed(Instant.parse("2025-11-02T00:00:00Z"), ZoneId.systemDefault()));
        scheduler.onSchedule(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);

        // THEN - on first date => execution
        assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(1);
        completeExecution();  // Simulate execution completed
        triggerExecutionPublisher.clear();

        // WHEN - move forward another date
        SchedulerClock.setClock(Clock.fixed(Instant.parse("2025-11-03T00:00:00Z"), ZoneId.systemDefault()));
        scheduler.onSchedule(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);

        // THEN - still not on second date => no execution
        assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(0);

        // WHEN - move forward to the second date
        SchedulerClock.setClock(Clock.fixed(Instant.parse("2025-11-04T00:00:00Z"), ZoneId.systemDefault()));
        scheduler.onSchedule(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);

        // THEN - on second date => execution
        assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(1);
        completeExecution();  // Simulate execution completed
        triggerExecutionPublisher.clear();

        // WHEN - move forward to the next date
        SchedulerClock.setClock(Clock.fixed(Instant.parse("2025-11-05T00:00:00Z"), ZoneId.systemDefault()));
        scheduler.onSchedule(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);

        // THEN - no execution
        assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(0);
        triggerExecutionPublisher.clear();
    }

    @Test
    void shouldSucceedScheduleConditionalScheduleTriggerGivenValidTimeZone() {
        // region [GIVEN]
        FlowWithSource flow = Fixtures.defaultFlow(builder -> builder
            // execute the workflow only if that day is the first Monday of the month.
            .cron("0 0 * * *")
            .conditions(List.of(
                DayWeekInMonth.builder()
                    .type(DayWeekInMonth.class.getName())
                    .date("{{ trigger.date }}")
                    .dayOfWeek(Property.ofValue(DayOfWeek.MONDAY))
                    .dayInMonth(Property.ofValue(DayWeekInMonth.DayInMonth.FIRST))
                    .build()
            ))
            .build()
        );
        // Start scheduler at some arbitrary date (e.g., 2025-11-01, which is a Saturday)
        SchedulerClock.setClock(Clock.fixed(Instant.parse("2025-11-01T00:00:00Z"), ZoneId.of("UTC")));

        TriggerScheduler scheduler = newTriggerScheduler(List.of(flow));
        scheduler.onStart(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS); // vNode are 0-based
        // endregion [GIVEN]

        // WHEN - move forward 1 day (to Sunday)
        SchedulerClock.offset(Duration.ofDays(1));
        scheduler.onSchedule(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);

        // THEN - still not Monday => no execution
        assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(0);

        // WHEN - move forward to first Monday (2025-11-03)
        SchedulerClock.setClock(Clock.fixed(Instant.parse("2025-11-03T00:00:00Z"), ZoneId.of("UTC")));
        scheduler.onSchedule(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);

        // THEN - first Monday of the month => should fire execution
        assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(1);
    }

    @Test
    void shouldRecoverMissingScheduleGivenALL() {
        // region [GIVEN]
        FlowWithSource flow = Fixtures.flowWithSchedulePT15M(TEST_TZ, builder -> builder
            .recoverMissedSchedules(RecoverMissedSchedules.ALL)
            .build()
        );
        // Create an initial state with a prior evaluation date
        TriggerState initialState = TriggerState
            .of(Fixtures.triggerId(), List.of(), false, 0)
            .evaluatedAt(SchedulerClock.getClock(), SchedulerClock.now().minusMinutes(15))
            .updateForNextEvaluationDate(SchedulerClock.getClock(), SchedulerClock.now());
        triggerStateStore.save(initialState);

        TriggerScheduler scheduler = newTriggerScheduler(List.of(flow));

        SchedulerClock.offset(Duration.ofMinutes(55)); // Move clock forward (4 missed scheduled)
        scheduler.onStart(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);
        // endregion [GIVEN]

        // [WHEN]
        // Simulate multiple 'onSchedule'
        IntStream.rangeClosed(0, 4).forEach(i -> {
            scheduler.onSchedule(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);

            // Assertions on TriggerState
            TriggerState currentTriggerState = triggerStateStore.find(Fixtures.triggerId()).orElse(null);
            assertThat(currentTriggerState).isNotNull();

            // [1-4 Calls] onSchedule 
            if (i < 4) {
                assertThat(currentTriggerState.isLocked()).isTrue();
                assertThat(currentTriggerState.getUpdatedAt()).isEqualTo(SchedulerClock.now().toInstant());
                assertThat(currentTriggerState.getNextEvaluationDate()).isEqualTo(initialSchedulerClock.instant().plus(Duration.ofMinutes(15L * (i + 1))));

                // Check an execution was created
                assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(1);

                Execution execution = triggerExecutionPublisher.executions().getFirst();
                assertThat(execution.getScheduleDate()).isEqualTo(SchedulerClock.now().toInstant());
                assertThat(execution.getTenantId()).isEqualTo(flow.getTenantId());
                assertThat(execution.getNamespace()).isEqualTo(flow.getNamespace());
                assertThat(execution.getFlowId()).isEqualTo(flow.getId());

                // Simulate execution completed
                completeExecution();

                triggerExecutionPublisher.clear();
            }
            // [5 Call] onSchedule 
            else {
                assertThat(currentTriggerState.isLocked()).isFalse();

                // Check no execution was created
                assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(0);
            }
            SchedulerClock.offset(Duration.ofSeconds(1));
        });
    }

    @Test
    void shouldRecoverMissingScheduleGivenLAST() {
        // region [GIVEN]
        FlowWithSource flow = Fixtures.flowWithSchedulePT15M(TEST_TZ, builder -> builder
            .recoverMissedSchedules(RecoverMissedSchedules.LAST)
            .build()
        );
        // Create an initial state with a prior evaluation date
        TriggerState initialState = TriggerState
            .of(Fixtures.triggerId(), List.of(), false, 0)
            .evaluatedAt(SchedulerClock.getClock(), SchedulerClock.now().minusMinutes(15))
            .updateForNextEvaluationDate(SchedulerClock.getClock(), SchedulerClock.now());
        triggerStateStore.save(initialState);

        TriggerScheduler scheduler = newTriggerScheduler(List.of(flow));
        SchedulerClock.offset(Duration.ofMinutes(55)); // Move clock forward (4 missed scheduled)
        scheduler.onStart(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);
        // endregion [GIVEN]

        // [WHEN]
        final ZonedDateTime expectedNextEvaluationNDate = ZonedDateTime.now(Clock.offset(initialSchedulerClock, Duration.ofHours(1)));
        // Simulate multiple 'onSchedule'
        IntStream.rangeClosed(0, 1).forEach(i -> {
            scheduler.onSchedule(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);

            // Assertions on TriggerState
            TriggerState currentTriggerState = triggerStateStore.find(Fixtures.triggerId()).orElse(null);
            assertThat(currentTriggerState).isNotNull();

            // [1st Call] onSchedule 
            if (i == 0) {
                assertThat(currentTriggerState.isLocked()).isTrue();
                assertThat(currentTriggerState.getEvaluatedAt()).isEqualTo(expectedNextEvaluationNDate.minusMinutes(15).toInstant());
                assertThat(currentTriggerState.getUpdatedAt()).isEqualTo(SchedulerClock.now().toInstant());
                assertThat(expectedNextEvaluationNDate);

                // Assert NO Execution
                assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(1);

                Execution execution = triggerExecutionPublisher.executions().getFirst();
                assertThat(execution.getScheduleDate()).isEqualTo(SchedulerClock.now().toInstant());
                assertThat(execution.getTenantId()).isEqualTo(flow.getTenantId());
                assertThat(execution.getNamespace()).isEqualTo(flow.getNamespace());
                assertThat(execution.getFlowId()).isEqualTo(flow.getId());

                // Simulate execution completed
                completeExecution();

                triggerExecutionPublisher.executions().clear();
            }
            // [2nd Call] onSchedule 
            else {
                assertThat(currentTriggerState.getNextEvaluationDate()).isEqualTo(expectedNextEvaluationNDate.toInstant());
                assertThat(currentTriggerState.isLocked()).isFalse();

                // Assert NO execution
                assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(0);
            }
            SchedulerClock.offset(Duration.ofSeconds(1));
        });
    }

    @Test
    void shouldRecoverMissingScheduleGivenNONE() {
        // region [GIVEN]
        FlowWithSource flow = Fixtures.flowWithSchedulePT15M(TEST_TZ, builder -> builder
            .recoverMissedSchedules(RecoverMissedSchedules.NONE)
            .build()
        );
        // Create an initial state with a prior evaluation date
        TriggerState initialState = TriggerState
            .of(Fixtures.triggerId(), List.of(), false, 0)
            .evaluatedAt(SchedulerClock.getClock(), SchedulerClock.now().minusMinutes(15))
            .updateForNextEvaluationDate(SchedulerClock.getClock(), SchedulerClock.now());
        triggerStateStore.save(initialState);

        TriggerScheduler scheduler = newTriggerScheduler(List.of(flow));

        SchedulerClock.offset(Duration.ofMinutes(55)); // Move clock forward (4 missed scheduled)
        scheduler.onStart(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);
        // endregion [GIVEN]

        // [WHEN]

        scheduler.onSchedule(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);
        // endregion [WHEN]

        // [THEN]
        final ZonedDateTime expectedNextEvaluationNDate = ZonedDateTime.now(Clock.offset(initialSchedulerClock, Duration.ofHours(1)));
        // Assert TriggerState
        TriggerState currentTriggerState = triggerStateStore.find(Fixtures.triggerId()).orElse(null);
        assertThat(currentTriggerState).isNotNull();

        assertThat(currentTriggerState.getEvaluatedAt()).isEqualTo(initialState.getEvaluatedAt());
        assertThat(currentTriggerState.getNextEvaluationDate()).isEqualTo(expectedNextEvaluationNDate.toInstant());
        assertThat(currentTriggerState.isLocked()).isFalse();

        // Assert NO execution
        assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(0);
        // endregion [THEN]
    }

    @Test
    void shouldNotScheduleScheduleTriggerWithDisabledTrue() {
        // region [GIVEN]
        FlowWithSource flow = Fixtures.flowWithSchedulePT15M(TEST_TZ);
        TriggerScheduler scheduler = newTriggerScheduler(List.of(flow));
        scheduler.onStart(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS); // vNode are 0-based
        // endregion [GIVEN]

        // WHEN
        TriggerState initialState = triggerStateStore.find(Fixtures.triggerId()).orElse(null);
        triggerStateStore.save(initialState
            .locked(SchedulerClock.getClock(), false)
            .disabled(SchedulerClock.getClock(), true)
        );

        SchedulerClock.offset(Duration.ofMinutes(15));
        scheduler.onSchedule(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);

        // THEN
        TriggerState state = triggerStateStore.find(Fixtures.triggerId()).orElse(null);
        assertThat(state).isNotNull();

        assertThat(state.isLocked()).isFalse();
        assertThat(state.getUpdatedAt()).isEqualTo(initialState.getUpdatedAt());

        // Check NO execution was created
        assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(0);
    }

    @Test
    void shouldScheduleScheduleTriggerWithBackfill() {
        // region [GIVEN]
        FlowWithSource flow = Fixtures.flowWithSchedulePT15M(TEST_TZ);

        TriggerScheduler scheduler = newTriggerScheduler(List.of(flow));

        scheduler.onStart(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);

        // Trigger an initial execution
        scheduler.onSchedule(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);

        TriggerState triggerState = triggerStateStore.find(Fixtures.triggerId()).orElse(null);
        assertThat(triggerState).isNotNull();
        assertThat(triggerState.getNextEvaluationDate()).isEqualTo(SchedulerClock.now().plusMinutes(15).toInstant());

        // endregion [GIVEN]

        // [WHEN]

        // Setup Backfill
        ZonedDateTime backfillStart = SchedulerClock.now().minus(Duration.ofHours(1));
        triggerStateStore.save(triggerState
            .locked(SchedulerClock.getClock(), false)
            .updateForNextEvaluationDate(SchedulerClock.getClock(), backfillStart)
            .backfill(SchedulerClock.getClock(), Backfill.builder().start(backfillStart).build())
        );

        // Simulate multiple 'onSchedule'
        IntStream.rangeClosed(0, 4).forEach(i -> {
            scheduler.onSchedule(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS);

            // Assertions on TriggerState
            TriggerState currentTriggerState = triggerStateStore.find(Fixtures.triggerId()).orElse(null);
            assertThat(currentTriggerState).isNotNull();
            assertThat(currentTriggerState.isLocked()).isTrue();
            assertThat(currentTriggerState.getUpdatedAt()).isEqualTo(SchedulerClock.now().toInstant());
            assertThat(currentTriggerState.getNextEvaluationDate()).isEqualTo(backfillStart.plus(Duration.ofMinutes(15L * (i + 1))).toInstant());

            // Check an execution was created
            assertThat(triggerExecutionPublisher.executions().size()).isEqualTo(1);

            Execution execution = triggerExecutionPublisher.executions().getFirst();
            assertThat(execution.getScheduleDate()).isEqualTo(SchedulerClock.now().toInstant());
            assertThat(execution.getTenantId()).isEqualTo(flow.getTenantId());
            assertThat(execution.getNamespace()).isEqualTo(flow.getNamespace());
            assertThat(execution.getFlowId()).isEqualTo(flow.getId());

            // Simulate execution completed
            completeExecution();

            triggerExecutionPublisher.executions().clear();

            // [1-4 Calls] onSchedule 
            if (i < 4) {
                assertThat(currentTriggerState.getBackfill()).isNotNull();
            }
            // [5 Call] onSchedule 
            else {
                assertThat(currentTriggerState.getBackfill()).isNull();
            }
            SchedulerClock.offset(Duration.ofSeconds(1));
        });

        // endregion [WHEN]
    }

    @Test
    void shouldFailInitMissingScheduleTriggerGivenInvalidTimeZone() {
        // region [GIVEN]
        FlowWithSource flow = Fixtures.flowWithSchedulePT15M("Asia/Delhi");
        TriggerScheduler scheduler = newTriggerScheduler(List.of(flow));
        // endregion [GIVEN]

        // WHEN
        scheduler.onStart(SchedulerClock.getClock(), SchedulerClock.now().toInstant(), NODES_ASSIGNMENTS); // vNode are 0-based

        // THEN
        TriggerState state = triggerStateStore.find(Fixtures.triggerId()).orElse(null);
        assertThat(state).isNull();
    }

    private void completeExecution() {
        triggerStateStore.find(Fixtures.triggerId()).ifPresent(state -> {
            TriggerState newState = state
                .updateForExecutionState(SchedulerClock.getClock(), State.Type.SUCCESS)
                .locked(SchedulerClock.getClock(), false);
            triggerStateStore.save(newState);
        });
    }

    private TriggerScheduler newTriggerScheduler(List<FlowWithSource> flows) {
        InMemoryFlowMetaStore flowMetaStore = new InMemoryFlowMetaStore(1, flows);
        return new TriggerScheduler(
            triggerStateStore,
            flowMetaStore,
            metricRegistry,
            runContextFactory,
            conditionService,
            pluginDefaultService,
            schedulableEvaluator,
            new DefaultSchedulableTriggerFetcher(runContextFactory, triggerStateStore, flowMetaStore, pluginDefaultService),
            triggerWorkerJobPublisher,
            triggerExecutionPublisher,
            new SchedulerConfiguration(1, Duration.ZERO, 100)
        );
    }

    @Plugin(internal = true)
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    public static class TestPollingTrigger extends AbstractTrigger implements PollingTriggerInterface {

        private Duration interval;

        private Property<String> value;

        @Override
        public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) {
            return Optional.of(TriggerService.generateExecution(this, conditionContext, context, Map.of()));
        }
    }

    @Plugin(internal = true)
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    public static class TestRealTimeTrigger extends AbstractTrigger implements RealtimeTriggerInterface {

        private Property<String> value;

        @Override
        public Publisher<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) {
            return Mono.just(TriggerService.generateExecution(this, conditionContext, context, Map.of()));
        }
    }
}