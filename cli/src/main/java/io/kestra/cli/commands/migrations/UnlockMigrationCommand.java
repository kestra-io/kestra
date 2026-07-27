package io.kestra.cli.commands.migrations;

import io.kestra.core.migration.MigrationLock;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

/**
 * CLI command that force-releases the migration lock.
 *
 * <p>
 * This is primarily useful for the Elasticsearch backend where the lock is a persistent
 * document that survives process crashes. For JDBC backends (PostgreSQL, MySQL), advisory locks
 * are session-scoped and auto-release when the holding connection closes.
 *
 * <p>
 * Usage: {@code kestra migrate unlock}
 */
@Slf4j
@CommandLine.Command(
    name = "unlock",
    description = "Force-release the migration lock",
    mixinStandardHelpOptions = true
)
public class UnlockMigrationCommand extends AbstractMigrationCommand {

    @Inject
    private MigrationLock migrationLock;

    @Override
    public Integer call() throws Exception {
        super.call();
        try {
            migrationLock.forceRelease();
            log.info("Migration unlock command completed.");
            return 0;
        } catch (Exception e) {
            log.error("Failed to force-release the migration lock: {}", e.getMessage());
            return 1;
        }
    }
}
