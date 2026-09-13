package io.kestra.jdbc.migration;

/**
 * Abstract base for the Fethr catch-up migration.
 *
 * <p>
 * Replays the upstream Flyway migrations {@code V1_45}-{@code V1_57}, which this fork never
 * applied because it used those version numbers for its own features ({@code V1_45__vault_secrets},
 * {@code V1_46__vault_credentials}, {@code V1_47__tables}, and so on). Upstream 1.3 databases
 * received those changes from Flyway; a Fethr database did not.
 *
 * <p>
 * Kestra 2.0 carries all of them in its baseline, but {@code MigrationRunner} records
 * {@code "0-init"} as applied <em>without executing it</em> once it detects a Flyway-managed
 * schema, so on the Fethr upgrade path nothing else supplies them.
 *
 * <p>
 * The script id sorts before {@code "2.0.01-schema"}, which is required: that script assumes the
 * repaired schema, most visibly where it declines to create {@code logs_tenant_timestamp} on the
 * grounds that upstream {@code V1_55} already did.
 *
 * <p>
 * Every statement is idempotent, so this is also a no-op on a fresh install (where {@code "0-init"}
 * has already done all of it) and on a database where it has already run.
 */
public abstract class AbstractV2_0_00FethrCatchupMigration extends AbstractSQLMigrationScript {

    @Override
    public String scriptId() {
        return "2.0.00-fethr-catchup";
    }

    @Override
    public String description() {
        return "Fethr: replay upstream 1.x migrations skipped by the fork's Flyway version collision";
    }
}
