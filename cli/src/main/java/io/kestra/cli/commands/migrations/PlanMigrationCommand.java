package io.kestra.cli.commands.migrations;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.migration.MigrationRunner;
import io.kestra.core.migration.MigrationScript;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.List;
import java.util.Map;

/**
 * CLI command that lists the pending database migration scripts without applying them.
 *
 * <p>This is the read-only counterpart of {@code kestra migrate run}: it reports the migrations
 * that would be applied (e.g. what an upgrade would run) and then exits without touching the data.
 * It is useful to prepare an upgrade by knowing exactly which migrations are planned.
 *
 * <p>Usage: {@code kestra migrate plan}
 */
@Slf4j
@CommandLine.Command(
    name = "plan",
    description = "Show the pending database migration scripts without applying them",
    mixinStandardHelpOptions = true
)
public class PlanMigrationCommand extends AbstractCommand {

    @Inject
    private MigrationRunner migrationRunner;

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        // Prevent the eager @Context MigrationRunner from applying migrations on startup,
        // so this command can report what is pending without changing the database.
        MigrationRunner.setSkipAutoRun(true);
        return Map.of();
    }

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
        }
        return 0;
    }
}
