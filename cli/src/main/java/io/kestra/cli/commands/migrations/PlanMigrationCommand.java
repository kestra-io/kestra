package io.kestra.cli.commands.migrations;

import java.util.List;

import io.kestra.core.migration.MigrationRunner;
import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.AbstractSQLMigrationScript;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

/**
 * CLI command that lists the pending database migration scripts without applying them.
 *
 * <p>
 * This is the read-only counterpart of {@code kestra migrate run}: it reports the migrations
 * that would be applied (e.g. what an upgrade would run) and then exits without touching the data.
 * It is useful to prepare an upgrade by knowing exactly which migrations are planned.
 *
 * <p>
 * Usage: {@code kestra migrate plan} (add {@code --sql} to also print the SQL of each migration).
 */
@Slf4j
@CommandLine.Command(
    name = "plan",
    description = "Show the pending database migration scripts without applying them",
    mixinStandardHelpOptions = true
)
public class PlanMigrationCommand extends AbstractMigrationCommand {

    @Inject
    private MigrationRunner migrationRunner;

    @CommandLine.Option(names = { "-s", "--sql" }, description = "Also print the SQL of each pending migration (SQL-based migrations only).")
    private boolean sql = false;

    @Override
    public Integer call() throws Exception {
        super.call();

        List<MigrationScript> pending = migrationRunner.pendingScripts();
        if (pending.isEmpty()) {
            stdOut("No pending migrations.");
            return 0;
        }

        stdOut(pending.size() + " pending migration(s):");
        for (MigrationScript script : pending) {
            stdOut("  " + script.scriptId() + " — " + script.description());

            if (sql) {
                printSql(script);
            }
        }
        return 0;
    }

    private void printSql(final MigrationScript script) throws Exception {
        List<String> resources = script.sqlResources();

        if (resources.isEmpty()) {
            stdOut("    (no SQL — Java-based migration)");
            return;
        }

        for (String resource : resources) {
            stdOut("");
            stdOut(AbstractSQLMigrationScript.readSqlResource(resource));
            stdOut("");
        }
    }
}
