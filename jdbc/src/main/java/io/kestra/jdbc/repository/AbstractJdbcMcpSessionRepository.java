package io.kestra.jdbc.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;

import io.kestra.core.mcp.models.McpSession;
import io.kestra.core.mcp.repositories.McpSessionRepositoryInterface;
import io.kestra.core.utils.IdUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Abstract JDBC implementation of {@link McpSessionRepositoryInterface}.
 * <p>
 * Sessions are ephemeral infrastructure state — no soft-delete, no audit events.
 */
@Slf4j
public abstract class AbstractJdbcMcpSessionRepository extends AbstractJdbcCrudRepository<McpSession>
    implements McpSessionRepositoryInterface {

    private static final Field<String> SERVER_ID_FIELD = field("server_id", String.class);
    private static final Field<String> SESSION_ID_FIELD = field("session_id", String.class);
    private static final Field<String> SSE_NODE_FIELD = field("sse_node", String.class);
    private static final Field<Timestamp> CREATED_AT_FIELD = DSL.field(DSL.name("created_at"), Timestamp.class);

    public AbstractJdbcMcpSessionRepository(io.kestra.jdbc.AbstractJdbcRepository<McpSession> jdbcRepository) {
        super(jdbcRepository);
    }

    /**
     * Skip the soft-delete filter: mcp_session has no deleted column.
     * {@inheritDoc}
     */
    @Override
    protected Condition defaultFilter(String tenantId) {
        return buildTenantCondition(tenantId);
    }

    /**
     * Skip the soft-delete filter: mcp_session has no deleted column.
     * {@inheritDoc}
     */
    @Override
    protected Condition defaultFilter() {
        return DSL.trueCondition();
    }

    @Override
    public Optional<McpSession> find(String tenantId, String serverId, String sessionId) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                Record record = context
                    .select(VALUE_FIELD)
                    .from(jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId))
                    .and(SERVER_ID_FIELD.eq(serverId))
                    .and(SESSION_ID_FIELD.eq(sessionId))
                    .fetchAny();
                return record == null ? Optional.empty() : Optional.of(jdbcRepository.map(record));
            });
    }

    @Override
    public List<McpSession> findByServerId(String tenantId, String serverId) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                return context
                    .select(VALUE_FIELD)
                    .from(jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId))
                    .and(SERVER_ID_FIELD.eq(serverId))
                    .fetch()
                    .map(jdbcRepository::map);
            });
    }

    @Override
    public List<McpSession> findBySseNode(String sseNode) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                return context
                    .select(VALUE_FIELD)
                    .from(jdbcRepository.getTable())
                    .where(SSE_NODE_FIELD.eq(sseNode))
                    .fetch()
                    .map(jdbcRepository::map);
            });
    }

    @Override
    public McpSession save(McpSession session) {
        this.jdbcRepository.persist(session);
        return session;
    }

    @Override
    public Optional<McpSession> delete(String tenantId, String sessionId) {
        Optional<McpSession> existing = findBySessionId(tenantId, sessionId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        this.jdbcRepository.delete(existing.get());
        return existing;
    }

    @Override
    public int purgeOlderThan(Instant threshold) {
        return jdbcRepository.getDslContextWrapper().transactionResult(configuration -> {
            DSLContext context = DSL.using(configuration);
            return context.deleteFrom(jdbcRepository.getTable())
                .where(CREATED_AT_FIELD.lt(Timestamp.from(threshold)))
                .execute();
        });
    }

    private Optional<McpSession> findBySessionId(String tenantId, String sessionId) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                Record record = context
                    .select(VALUE_FIELD)
                    .from(jdbcRepository.getTable())
                    .where(KEY_FIELD.eq(IdUtils.fromParts(tenantId, sessionId)))
                    .fetchAny();
                return record == null ? Optional.empty() : Optional.of(jdbcRepository.map(record));
            });
    }
}
