package io.kestra.cli.commands.migrations;

import io.kestra.cli.AbstractCommand;

/**
 * Base class for the pure database-migration commands ({@code migrate run/plan/unlock/repair}).
 *
 * <p>
 * Commands extending this class are executed against a <em>minimal</em> {@code ApplicationContext}:
 * {@code Kestra} detects the marker and builds a context that does not register any Kestra
 * {@code @Context} bean (repositories, server services, and the migration startup trigger). Only the
 * lazily-resolved {@code MigrationRunner} / {@code MigrationLock} and their {@code DataSource} are
 * started, so no other bean touches the database before the migration runs.
 *
 * <p>
 * This is why these commands need no "skip auto-run" flag: the {@code @Context} startup trigger
 * simply never exists in their context, so they resolve the runner and invoke it explicitly.
 *
 * <p>
 * Migrations do not need plugins, so external plugin loading is disabled.
 */
public abstract class AbstractMigrationCommand extends AbstractCommand {

    @Override
    protected boolean loadExternalPlugins() {
        return false;
    }

    @Override
    protected boolean isPluginManagerEnabled() {
        return false;
    }
}
