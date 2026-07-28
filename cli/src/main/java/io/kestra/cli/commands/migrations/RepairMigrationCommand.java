package io.kestra.cli.commands.migrations;

import io.kestra.core.migration.MigrationLockedException;
import io.kestra.core.migration.MigrationRunnerInterface;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

/**
 * CLI command that repairs the stored checksum for an already-applied migration script.
 *
 * <p>
 * Usage: {@code kestra migrate repair 2.0.01-upgrade}
 */
@Slf4j
@CommandLine.Command(
    name = "repair",
    description = "Repair the stored checksum for an already-applied migration script",
    mixinStandardHelpOptions = true
)
public class RepairMigrationCommand extends AbstractMigrationCommand {

    @CommandLine.Parameters(
        index = "0",
        paramLabel = "<script-id>",
        description = "Migration script ID to repair, for example 2.0.01-upgrade"
    )
    private String scriptId;

    @Inject
    private MigrationRunnerInterface migrationRunner;

    @Override
    public Integer call() throws Exception {
        super.call();
        try {
            migrationRunner.repairChecksum(scriptId);
            log.info("Migration checksum repair completed.");
            return 0;
        } catch (MigrationLockedException e) {
            log.error(e.getMessage());
            return 1;
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            log.error(e.getMessage());
            return 1;
        }
    }
}
