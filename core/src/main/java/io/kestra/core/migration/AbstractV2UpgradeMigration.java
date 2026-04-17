package io.kestra.core.migration;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.store.TriggerStateStore;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Abstract base for the Kestra 2.0 upgrade migration script.
 *
 * <p>Handles the backend-agnostic part of the 2.0 upgrade: migrating V1 {@link Trigger} rows
 * to the new {@link TriggerState} store. Concrete subclasses are responsible for applying the
 * backend-specific schema changes (SQL DDL, Elasticsearch index mappings, …) via
 * {@link #doSchemaUpgrade()}.
 *
 * <p>The trigger migration is intentionally automated here (rather than left as a manual CLI
 * command) because the dataset is small and the operation is fast.
 */
@Slf4j
public abstract class AbstractV2UpgradeMigration implements MigrationScript {

    private final TriggerRepositoryInterface triggerRepository;
    private final TriggerStateStore triggerStateStore;
    private final SchedulerConfiguration schedulerConfiguration;

    protected AbstractV2UpgradeMigration(
        final TriggerRepositoryInterface triggerRepository,
        final TriggerStateStore triggerStateStore,
        final SchedulerConfiguration schedulerConfiguration
    ) {
        this.triggerRepository = triggerRepository;
        this.triggerStateStore = triggerStateStore;
        this.schedulerConfiguration = schedulerConfiguration;
    }

    @Override
    public final String scriptId() {
        return "2.0";
    }

    /**
     * Applies the backend-specific schema changes for the 2.0 upgrade.
     *
     * <p>Called before the trigger data migration so the {@code trigger_states} table (or
     * equivalent) exists when {@link #migrateTriggers()} runs.
     */
    protected abstract void doSchemaUpgrade() throws Exception;

    @Override
    public final void migrate() throws Exception {
        doSchemaUpgrade();
        migrateTriggers();
    }

    private void migrateTriggers() {
        List<Trigger> triggers = triggerRepository.findAllForAllTenantsV1();
        log.info("Migrating {} V1 trigger(s) to TriggerState...", triggers.size());
        for (Trigger trigger : triggers) {
            try {
                TriggerState migrated = trigger.toTriggerState(schedulerConfiguration.vnodes());
                triggerStateStore.save(migrated);
            } catch (Exception e) {
                log.error("Failed to migrate trigger {}/{}/{}", trigger.getTenantId(), trigger.getNamespace(), trigger.getTriggerId(), e);
                throw e;
            }
        }
        log.info("Trigger migration complete.");
    }
}
