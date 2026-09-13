package io.kestra.core.ai.agent.repositories;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.ai.agent.models.AgentThreadStatus;

/**
 * Durable, tenant-scoped store for {@link AgentThread} Copilot conversation threads.
 */
public interface AiThreadRepositoryInterface {
    /**
     * Finds a non-deleted thread by its tenant and uid.
     *
     * @param tenant the tenant the thread belongs to.
     * @param uid the unique identifier of the thread.
     * @return the thread, or an empty {@link Optional} if it does not exist or is deleted.
     */
    Optional<AgentThread> find(String tenant, String uid);

    /**
     * Lists every non-deleted thread owned by a user within a tenant. Ordering is not guaranteed here
     * (the manager sorts for presentation). Used by the EE thread-management surface; OSS runs a single
     * implicit session and never lists.
     *
     * @param tenant the tenant the threads belong to.
     * @param userId the user that owns the threads; {@code null} matches threads with no owner (OSS).
     * @return the owned, non-deleted threads (possibly empty).
     */
    List<AgentThread> findAllForUser(String tenant, String userId);

    /**
     * Returns whether a non-deleted thread exists for the given tenant and uid.
     *
     * @param tenant the tenant the thread belongs to.
     * @param uid the unique identifier of the thread.
     * @return {@code true} if a matching, non-deleted thread exists.
     */
    boolean exists(String tenant, String uid);

    /**
     * Persists a newly created thread.
     *
     * @param thread the thread to store, carrying its own tenant and uid.
     * @return the stored thread.
     */
    AgentThread create(AgentThread thread);

    /**
     * Persists the current state of a thread, overwriting any existing entry.
     *
     * @param thread the thread to save.
     * @return the saved thread.
     */
    AgentThread save(AgentThread thread);

    /**
     * Soft-deletes a thread, retaining its row (and history) until purge. Idempotent: deleting an
     * already-deleted thread is a no-op from the caller's perspective (subsequent lookups miss).
     *
     * @param thread the thread to soft-delete.
     * @return the soft-deleted thread.
     */
    AgentThread delete(AgentThread thread);

    /**
     * Atomically applies a mutation to a thread only if it exists, is not deleted, and its
     * status matches {@code expected} (compare-and-set). This is the turn single-flight guard.
     *
     * @param tenant the tenant the thread belongs to.
     * @param uid the unique identifier of the thread.
     * @param expected the status the thread must currently be in for the mutation to apply.
     * @param mutation the transformation to apply to the matched thread; must not return {@code null}.
     * @return the updated thread, or an empty {@link Optional} if the expected status did not match.
     */
    Optional<AgentThread> updateIf(String tenant, String uid, AgentThreadStatus expected, UnaryOperator<AgentThread> mutation);
}
