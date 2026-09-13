package io.kestra.jdbc.migration;

/**
 * Abstract base for the Fethr schema baseline.
 *
 * <p>
 * Creates the tables behind the Fethr features carried onto 2.0: {@code secrets},
 * {@code credentials} and the {@code tables} registry. Consolidated from the fork's Flyway scripts
 * {@code V1_45__vault_secrets}, {@code V1_46__vault_credentials} and {@code V1_47__tables}, which
 * no longer exist under 2.0's migration framework.
 *
 * <p>
 * The script id uses the {@code 999999999} sentinel increment so it sorts after every upstream
 * {@code 2.0.x} script under their documented two-digit convention, and the {@code -01-} sequence
 * orders it against later Fethr scripts. The sentinel is per-minor: work on 2.1 needs
 * {@code 2.1.999999999-fethr-*}.
 *
 * <p>
 * Every statement is idempotent, so this is a no-op against a Fethr database upgraded in place
 * (where the tables already exist from Flyway) and creates them on a fresh install.
 *
 * <p>
 * Out of scope for this wave, and so deliberately absent: {@code agent_*} (Claire),
 * {@code import_*}, {@code node_catalog} and {@code schema_catalog}. They rejoin here when their
 * features are ported.
 */
public abstract class AbstractV2_0Fethr01SchemaMigration extends AbstractSQLMigrationScript {

    @Override
    public String scriptId() {
        return "2.0.999999999-fethr-01-schema";
    }

    @Override
    public String description() {
        return "Fethr schema: secrets, credentials and the tables registry";
    }
}
