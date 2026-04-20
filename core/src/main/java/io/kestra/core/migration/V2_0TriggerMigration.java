package io.kestra.core.migration;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.store.TriggerStateStore;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Migrates V1 {@link Trigger} rows to the new {@link TriggerState} store.
 *
 * <p>Runs after all schema upgrade scripts ({@code "2.0"}, {@code "2.0-ee"}, {@code "2.0-queue"},
 * etc.) so the {@code trigger_states} table already exists. Excluded from WORKER server type
 * because repositories are not available there.
 */
@Slf4j
@Singleton
@Requires(property = "kestra.server-type", notEquals = "WORKER")
public class V2_0TriggerMigration implements MigrationScript {

    private final TriggerRepositoryInterface triggerRepository;
    private final TriggerStateStore triggerStateStore;
    private final SchedulerConfiguration schedulerConfiguration;

    @Inject
    public V2_0TriggerMigration(
        final TriggerRepositoryInterface triggerRepository,
        final TriggerStateStore triggerStateStore,
        final SchedulerConfiguration schedulerConfiguration
    ) {
        this.triggerRepository = triggerRepository;
        this.triggerStateStore = triggerStateStore;
        this.schedulerConfiguration = schedulerConfiguration;
    }

    @Override
    public String scriptId() {
        return "2.0-triggers";
    }

    @Override
    public String description() {
        return "Migrate V1 trigger rows to TriggerState";
    }

    @Override
    public String checksum() {
        return null;
    }

    @Override
    public void migrate() throws Exception {
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
