package io.kestra.webserver.services.ai.agent.store;

import java.util.Optional;
import java.util.function.UnaryOperator;

import io.kestra.webserver.services.ai.agent.domain.AgentThread;
import io.kestra.webserver.services.ai.agent.domain.AgentThreadStatus;

/**
 * Store for {@link AgentThread} Copilot conversation threads, scoped by tenant.
 */
public interface ThreadStore {
    /**
     * Persists a newly created thread.
     *
     * @param thread the thread to store, carrying its own tenant and uid.
     * @return the stored thread.
     */
    AgentThread create(AgentThread thread);

    /**
     * Finds a non-deleted thread by its tenant and uid.
     *
     * @param tenant the tenant the thread belongs to.
     * @param uid    the unique identifier of the thread.
     * @return the thread, or an empty {@link Optional} if it does not exist or is deleted.
     */
    Optional<AgentThread> find(String tenant, String uid);

    /**
     * Returns whether a non-deleted thread exists for the given tenant and uid.
     *
     * @param tenant the tenant the thread belongs to.
     * @param uid    the unique identifier of the thread.
     * @return {@code true} if a matching, non-deleted thread exists.
     */
    boolean exists(String tenant, String uid);

    /**
     * Persists the current state of a thread, overwriting any existing entry.
     *
     * @param thread the thread to save.
     * @return the saved thread.
     */
    AgentThread save(AgentThread thread);

    /**
     * Atomically applies a mutation to a thread only if it exists, is not deleted, and its
     * status matches {@code expected} (compare-and-set).
     *
     * @param tenant   the tenant the thread belongs to.
     * @param uid      the unique identifier of the thread.
     * @param expected the status the thread must currently be in for the mutation to apply.
     * @param mutation the transformation to apply to the matched thread; must not return {@code null}.
     * @return the updated thread, or an empty {@link Optional} if the expected status did not match.
     */
    Optional<AgentThread> updateIf(String tenant, String uid, AgentThreadStatus expected, UnaryOperator<AgentThread> mutation);
}
