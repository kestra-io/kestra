package io.kestra.jdbc.repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.ai.agent.models.AgentThreadStatus;
import io.kestra.core.ai.agent.repositories.AiThreadRepositoryInterface;

/**
 * Abstract JDBC implementation of {@link AiThreadRepositoryInterface}: Copilot conversation threads,
 * tenant-scoped and soft-deleted (the default {@link #defaultFilter(String)} already excludes deleted
 * rows).
 */
public abstract class AbstractJdbcAgentThreadRepository extends AbstractJdbcCrudRepository<AgentThread>
    implements AiThreadRepositoryInterface {

    public AbstractJdbcAgentThreadRepository(io.kestra.jdbc.AbstractJdbcRepository<AgentThread> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    public Optional<AgentThread> find(String tenant, String uid) {
        return findOne(tenant, KEY_FIELD.eq(uid));
    }

    /**
     * Lists the tenant's non-deleted threads (the default filter excludes deleted rows) and keeps only
     * those owned by {@code userId}. The {@code ai_agent_thread} table has no {@code user_id} column, so
     * ownership is matched against the deserialized record rather than in SQL — mirroring
     * {@link #find(String, String, String)}.
     * {@inheritDoc}
     */
    @Override
    public List<AgentThread> findAllForUser(String tenant, String userId) {
        return find(tenant, DSL.trueCondition()).stream()
            .filter(thread -> Objects.equals(thread.userId(), userId))
            .toList();
    }

    @Override
    public boolean exists(String tenant, String uid) {
        return find(tenant, uid).isPresent();
    }

    /**
     * Compare-and-set: within a single transaction, locks the matching non-deleted row, applies the
     * mutation only if its status equals {@code expected}, and persists the result. Returns empty when
     * the row is missing, deleted, or in a different status — the cross-node single-flight guard.
     */
    @Override
    public Optional<AgentThread> updateIf(String tenant, String uid, AgentThreadStatus expected, UnaryOperator<AgentThread> mutation) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);
                Record record = context
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(this.defaultFilter(tenant))
                    .and(KEY_FIELD.eq(uid))
                    .forUpdate()
                    .fetchAny();

                if (record == null) {
                    return Optional.empty();
                }

                AgentThread existing = this.jdbcRepository.map(record);
                if (existing.status() != expected) {
                    return Optional.empty();
                }

                AgentThread updated = Objects.requireNonNull(mutation.apply(existing), "mutation must not return null");
                this.save(context, updated);
                return Optional.of(updated);
            });
    }
}
