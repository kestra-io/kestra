package io.kestra.core.migration;

import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base for the Kestra 2.0 upgrade migration script.
 *
 * <p>
 * Concrete subclasses apply backend-specific schema changes (SQL DDL, Elasticsearch
 * index mappings, …) via {@link #doSchemaUpgrade()}. Trigger data migration is handled
 * separately by {@link V2_0_07TriggerMigration}.
 */
@Slf4j
public abstract class AbstractV2_0_01UpgradeMigration implements MigrationScript {

    @Override
    public final String scriptId() {
        return "2.0.01-upgrade";
    }

    /**
     * Applies the backend-specific schema changes for the 2.0 upgrade.
     */
    protected abstract void doSchemaUpgrade() throws Exception;

    @Override
    public final void migrate() throws Exception {
        doSchemaUpgrade();
    }
}
