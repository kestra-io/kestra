package io.kestra.core.repositories.log;

import org.slf4j.event.Level;

import io.kestra.core.models.AccessScope;

/**
 * Access-control + audit collaborator for {@link io.kestra.core.repositories.LogDataStoreInterface}
 * backends.
 * <p>
 * The backend consults it at its {@code defaultFilter} chokepoint and translates the returned
 * {@link AccessScope} into its own query language, and calls {@link #onDeleteByQuery} to let the
 * implementation react to a deleted (e.g. publish an audit event). This keeps the access-control
 * <em>policy</em> in one place (a single EE bean) while the <em>translation</em> stays in each
 * dialect-aware backend — so there is no per-backend ACL subclass.
 * <p>
 * OSS provides a {@link #GLOBAL} no-op default; EE replaces the bean with a
 * {@code CurrentUserContext}-based implementation.
 */
public interface LogDataStoreAccessControl {

    /**
     * A no-op collaborator granting global access with no auditing. Used as the safe default for log
     * repositories that are not wired to a real access-control bean.
     */
    LogDataStoreAccessControl GLOBAL = AccessScope::global;

    /**
     * @return the access scope the current caller has on logs (global, a set of namespaces, or none).
     */
    AccessScope namespaceScope();

    /**
     * Invoked before a {@code deleteByQuery(tenantId, executionId, taskId, taskRunId, minLevel, attempt)}.
     * Default no-op; EE publishes an audit event.
     */
    default void onDeleteByQuery(String tenantId, String executionId, String taskId, String taskRunId, Level minLevel, Integer attempt) {
        // no-op by default
    }
}
