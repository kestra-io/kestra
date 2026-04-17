package io.kestra.core.migration;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.store.TriggerStateStore;
import io.kestra.core.scheduler.vnodes.VNodes;
import io.kestra.plugin.core.dashboard.data.Triggers;
import io.micronaut.data.model.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AbstractV2UpgradeMigration} trigger migration logic.
 */
class AbstractV2UpgradeMigrationTest {

    private static final int VNODES = 16;
    private static final SchedulerConfiguration SCHEDULER_CONFIG =
        new SchedulerConfiguration(VNODES, Duration.ofSeconds(5), 100);

    private TrackingTriggerStateStore store;
    private List<String> schemaUpgradeCalls;

    @BeforeEach
    void setUp() {
        store = new TrackingTriggerStateStore();
        schemaUpgradeCalls = new ArrayList<>();
    }

    @Test
    void migrate_callsSchemaUpgradeThenMigratesTriggers() throws Exception {
        List<String> callOrder = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now();
        Trigger trigger1 = trigger("ns", "flow1", "cron", now, now.plusHours(1));
        Trigger trigger2 = trigger("ns", "flow2", "schedule", now, now.plusHours(2));

        ConcreteUpgradeMigration migration = new ConcreteUpgradeMigration(
            List.of(trigger1, trigger2), store, SCHEDULER_CONFIG,
            () -> callOrder.add("schema")
        );

        migration.migrate();

        // Schema upgrade ran first
        assertThat(callOrder).containsExactly("schema");

        // Both triggers saved
        assertThat(store.saved).hasSize(2);

        TriggerState saved1 = store.saved.get(0);
        assertThat(saved1.getNamespace()).isEqualTo("ns");
        assertThat(saved1.getFlowId()).isEqualTo("flow1");
        assertThat(saved1.getTriggerId()).isEqualTo("cron");
        assertThat(saved1.getEvaluatedAt()).isEqualTo(now.toInstant());
        assertThat(saved1.getNextEvaluationDate()).isEqualTo(now.plusHours(1).toInstant());
        assertThat(saved1.getVnode()).isEqualTo(VNodes.computeVNodeFromTrigger(trigger1, VNODES));
        assertThat(saved1.isLocked()).isFalse();

        TriggerState saved2 = store.saved.get(1);
        assertThat(saved2.getFlowId()).isEqualTo("flow2");
        assertThat(saved2.getVnode()).isEqualTo(VNodes.computeVNodeFromTrigger(trigger2, VNODES));
    }

    @Test
    void migrate_lockedWhenTriggerHasExecutionId() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        Trigger locked = Trigger.builder()
            .namespace("ns").flowId("flow").triggerId("t")
            .date(now).nextExecutionDate(now.plusHours(1))
            .executionId("exec-123")
            .build();

        ConcreteUpgradeMigration migration = new ConcreteUpgradeMigration(
            List.of(locked), store, SCHEDULER_CONFIG, () -> {}
        );

        migration.migrate();

        assertThat(store.saved).hasSize(1);
        assertThat(store.saved.get(0).isLocked()).isTrue();
    }

    @Test
    void migrate_withNoTriggers_onlyCallsSchemaUpgrade() throws Exception {
        List<String> callOrder = new ArrayList<>();
        ConcreteUpgradeMigration migration = new ConcreteUpgradeMigration(
            List.of(), store, SCHEDULER_CONFIG,
            () -> callOrder.add("schema")
        );

        migration.migrate();

        assertThat(callOrder).containsExactly("schema");
        assertThat(store.saved).isEmpty();
    }

    @Test
    void migrate_propagatesSchemaUpgradeFailure() {
        ConcreteUpgradeMigration migration = new ConcreteUpgradeMigration(
            List.of(), store, SCHEDULER_CONFIG,
            () -> { throw new RuntimeException("schema failed"); }
        );

        assertThatThrownBy(migration::migrate).hasMessage("schema failed");
        assertThat(store.saved).isEmpty();
    }

    @Test
    void migrate_propagatesTriggerMigrationFailure() {
        ZonedDateTime now = ZonedDateTime.now();
        Trigger trigger = trigger("ns", "flow", "t", now, now.plusHours(1));

        store.failOnSave = true;
        ConcreteUpgradeMigration migration = new ConcreteUpgradeMigration(
            List.of(trigger), store, SCHEDULER_CONFIG, () -> {}
        );

        assertThatThrownBy(migration::migrate).hasMessage("save failed");
        assertThat(store.saved).isEmpty();
    }

    // --- Helpers ---

    private Trigger trigger(String namespace, String flowId, String triggerId,
                            ZonedDateTime date, ZonedDateTime nextExecutionDate) {
        return Trigger.builder()
            .namespace(namespace)
            .flowId(flowId)
            .triggerId(triggerId)
            .date(date)
            .nextExecutionDate(nextExecutionDate)
            .build();
    }

    /**
     * Concrete subclass of {@link AbstractV2UpgradeMigration} for testing.
     */
    private static class ConcreteUpgradeMigration extends AbstractV2UpgradeMigration {

        private final List<Trigger> triggers;
        private final ThrowingRunnable schemaUpgrade;

        ConcreteUpgradeMigration(
            List<Trigger> triggers,
            TriggerStateStore store,
            SchedulerConfiguration config,
            ThrowingRunnable schemaUpgrade
        ) {
            super(new StubTriggerRepository(triggers), store, config);
            this.triggers = triggers;
            this.schemaUpgrade = schemaUpgrade;
        }

        @Override
        protected void doSchemaUpgrade() throws Exception {
            schemaUpgrade.run();
        }

        @Override
        public String description() { return "test migration"; }

        @Override
        public String checksum() { return "test-checksum"; }
    }

    /**
     * Stub {@link TriggerRepositoryInterface} returning a fixed list from
     * {@code findAllForAllTenantsV1()}. All other methods throw {@link UnsupportedOperationException}.
     */
    private static class StubTriggerRepository implements TriggerRepositoryInterface {

        private final List<Trigger> triggers;

        StubTriggerRepository(List<Trigger> triggers) {
            this.triggers = triggers;
        }

        @Override
        @SuppressWarnings("removal")
        public List<Trigger> findAllForAllTenantsV1() {
            return triggers;
        }

        @Override public Optional<TriggerState> findById(TriggerId trigger) { throw new UnsupportedOperationException(); }
        @Override public List<TriggerState> findAll(String tenantId) { throw new UnsupportedOperationException(); }
        @Override public List<TriggerState> findAllForAllTenants() { throw new UnsupportedOperationException(); }
        @Override public ArrayListTotal<TriggerState> find(Pageable from, String query, String tenantId, String namespace, String flowId, String workerId) { throw new UnsupportedOperationException(); }
        @Override public ArrayListTotal<TriggerState> find(Pageable from, String tenantId, List<QueryFilter> filters) { throw new UnsupportedOperationException(); }
        @Override public long countAll(String tenantId) { throw new UnsupportedOperationException(); }
        @Override public Flux<TriggerState> find(String tenantId, List<QueryFilter> filters) { throw new UnsupportedOperationException(); }
        @Override public Triggers.Fields dateFilterField() { throw new UnsupportedOperationException(); }
        @Override public ArrayListTotal<Map<String, Object>> fetchData(String tenantId, DataFilter<Triggers.Fields, ? extends ColumnDescriptor<Triggers.Fields>> filter, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable) throws IOException { throw new UnsupportedOperationException(); }
        @Override public Double fetchValue(String tenantId, DataFilterKPI<Triggers.Fields, ? extends ColumnDescriptor<Triggers.Fields>> descriptors, ZonedDateTime startDate, ZonedDateTime endDate, boolean numeratorFilter) throws IOException { throw new UnsupportedOperationException(); }
    }

    /**
     * Tracking {@link TriggerStateStore} that records saved states.
     */
    private static class TrackingTriggerStateStore implements TriggerStateStore {

        final List<TriggerState> saved = new ArrayList<>();
        boolean failOnSave = false;

        @Override
        public TriggerState save(TriggerState state) {
            if (failOnSave) throw new RuntimeException("save failed");
            saved.add(state);
            return state;
        }

        @Override public List<TriggerState> findTriggersEligibleForScheduling(ZonedDateTime now, Set<Integer> vNodes, boolean locked) { return List.of(); }
        @Override public List<TriggerState> findAllForVNodes(Set<Integer> vNodes) { return List.of(); }
        @Override public Optional<TriggerState> findById(io.kestra.core.models.triggers.TriggerId triggerId) { return Optional.empty(); }
        @Override public void delete(io.kestra.core.models.triggers.TriggerId triggerId) {}
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
