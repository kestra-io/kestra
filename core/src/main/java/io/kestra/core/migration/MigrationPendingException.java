package io.kestra.core.migration;

import io.kestra.core.exceptions.KestraRuntimeException;

import java.util.List;

/**
 * Thrown at startup when pending migrations exist and automatic migration is disabled.
 *
 * <p>Used by the EE migration runner override when {@code kestra.migration.auto=false}
 * (the EE default) and there are unapplied migration scripts. The application refuses to start
 * until the operator either runs {@code kestra migrate run} or sets
 * {@code kestra.migration.auto=true}.
 */
public class MigrationPendingException extends KestraRuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> pendingScriptIds;

    /**
     * Creates a new {@link MigrationPendingException}.
     *
     * @param pendingScriptIds the IDs of the migration scripts that have not yet been applied
     */
    public MigrationPendingException(final List<String> pendingScriptIds) {
        super(buildMessage(pendingScriptIds));
        this.pendingScriptIds = List.copyOf(pendingScriptIds);
    }

    /**
     * Returns the IDs of the pending migration scripts.
     *
     * @return an unmodifiable list of pending script IDs
     */
    public List<String> pendingScriptIds() {
        return pendingScriptIds;
    }

    private static String buildMessage(final List<String> pendingScriptIds) {
        return """
            Kestra cannot start: one or more database schema migrations are needed first.
            Migration scripts to apply: %s
            To run the migrations, either:
              - Run: kestra migrate run
              - Or enable automatic migration by setting: kestra.migration.auto=true
            """.formatted(pendingScriptIds);
    }
}
