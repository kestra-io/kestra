package io.kestra.cli.commands.migrations;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.migration.MigrationLockedException;
import io.kestra.core.migration.MigrationRunner;
import io.kestra.core.migration.MigrationRunnerInterface;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.Map;

/**
 * CLI command that explicitly applies all pending database migration scripts.
 *
 * <p>This command is primarily useful in EE where automatic migration on startup is disabled
 * by default ({@code kestra.migration.auto=false}). Running {@code kestra migrate run} applies
 * all pending scripts unconditionally, regardless of the {@code kestra.migration.auto} setting.
 *
 * <p>In OSS, migrations always run automatically, but this command can still be used to apply
 * migrations manually before starting the server (e.g. in a rolling-upgrade scenario).
 *
 * <p>Usage: {@code kestra migrate run}
 */
@Slf4j
@CommandLine.Command(
    name = "run",
    description = "Apply all pending database migration scripts",
    mixinStandardHelpOptions = true
)
public class RunMigrationCommand extends AbstractCommand {

    @Inject
    private MigrationRunnerInterface migrationRunner;

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        MigrationRunner.setSkipAutoRun(true);
        return Map.of();
    }

    @Override
    public Integer call() throws Exception {
        super.call();
        try {
            migrationRunner.runOrFailIfLocked();
            log.info("All pending migrations have been applied successfully.");
            return 0;
        } catch (MigrationLockedException e) {
            log.error(e.getMessage());
            return 1;
        }
    }
}
