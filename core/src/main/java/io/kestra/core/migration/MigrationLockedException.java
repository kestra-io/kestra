package io.kestra.core.migration;

import io.kestra.core.exceptions.KestraRuntimeException;

/**
 * Thrown by {@link MigrationRunnerInterface#runOrFailIfLocked()} when the migration lock is
 * already held by another process.
 *
 * <p>This is used by the {@code kestra migrate run} CLI command, which is always single-node
 * and should fail immediately instead of waiting for the lock.
 */
public class MigrationLockedException extends KestraRuntimeException {

    private static final long serialVersionUID = 1L;

    public MigrationLockedException() {
        super("Migration lock is held by another process. " +
              "Another instance may be running migrations. " +
              "If this is unexpected, use 'kestra migrate unlock' to force-release the lock.");
    }
}
