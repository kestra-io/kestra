package io.kestra.jdbc.repository;

import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.ai.agent.repositories.MessageStore;

/**
 * Abstract JDBC implementation of {@link MessageStore}: append-only conversation history keyed by
 * {@code thread_id}. Messages are not tenant-scoped or soft-deleted, so the default filter is
 * dropped; a thread's history sorts chronologically by the monotonic {@code key} (message uid).
 */
public abstract class AbstractJdbcAgentMessageRepository extends AbstractJdbcCrudRepository<AgentMessage>
    implements MessageStore {

    private static final Field<String> THREAD_ID_FIELD = field("thread_id", String.class);

    public AbstractJdbcAgentMessageRepository(io.kestra.jdbc.AbstractJdbcRepository<AgentMessage> jdbcRepository) {
        super(jdbcRepository);
    }

    /** No tenant column on agent_message. {@inheritDoc} */
    @Override
    protected Condition defaultFilter(String tenantId) {
        return DSL.trueCondition();
    }

    /** No tenant column on agent_message. {@inheritDoc} */
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
    public List<AgentMessage> load(String threadId) {
        return find((String) null, THREAD_ID_FIELD.eq(threadId), KEY_FIELD.asc());
    }
}
