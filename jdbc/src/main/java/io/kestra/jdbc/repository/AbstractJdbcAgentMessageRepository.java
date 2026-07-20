package io.kestra.jdbc.repository;

import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.ai.agent.repositories.AiMessageRepositoryInterface;

/**
 * Abstract JDBC implementation of {@link AiMessageRepositoryInterface}: append-only conversation
 * history keyed by {@code thread_id} and scoped by {@code tenant_id}. Messages are tenant-scoped (so a
 * thread's history never leaks across tenants) but never soft-deleted, so the default filter keeps only
 * the tenant condition and drops the {@code deleted} clause; a thread's history sorts chronologically by
 * the monotonic {@code key} (message uid).
 */
public abstract class AbstractJdbcAgentMessageRepository extends AbstractJdbcCrudRepository<AgentMessage>
    implements AiMessageRepositoryInterface {

    private static final Field<String> THREAD_ID_FIELD = field("thread_id", String.class);

    public AbstractJdbcAgentMessageRepository(io.kestra.jdbc.AbstractJdbcRepository<AgentMessage> jdbcRepository) {
        super(jdbcRepository);
    }

    /** Tenant-scoped, but no {@code deleted} column on ai_agent_message. {@inheritDoc} */
    @Override
    protected Condition defaultFilter(String tenantId) {
        return buildTenantCondition(tenantId);
    }

    /** No tenant argument here and no {@code deleted} column on ai_agent_message. {@inheritDoc} */
    @Override
    protected Condition defaultFilter() {
        return DSL.trueCondition();
    }

    @Override
    public AgentMessage append(AgentMessage message) {
        this.jdbcRepository.persist(message);
        return message;
    }

    @Override
    public List<AgentMessage> load(String tenant, String threadId) {
        return find(tenant, THREAD_ID_FIELD.eq(threadId), KEY_FIELD.asc());
    }
}
