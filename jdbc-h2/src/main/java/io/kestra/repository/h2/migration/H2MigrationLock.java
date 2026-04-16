package io.kestra.repository.h2.migration;

import io.kestra.core.migration.MigrationLock;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * No-op {@link MigrationLock} for H2.
 *
 * <p>H2 is an embedded, single-process database, so distributed locking is not needed.
 * <p>Active only when H2 is the <em>repository</em> backend, not just the queue, to avoid
 * conflicting with the Elasticsearch repository backend when H2 is used only as the queue.
 */
@Singleton
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
public class H2MigrationLock implements MigrationLock {

    @Override
    public void acquire() {
        // No-op: H2 is embedded and single-process
    }

    @Override
    public void release() {
        // No-op: H2 is embedded and single-process
    }
}
