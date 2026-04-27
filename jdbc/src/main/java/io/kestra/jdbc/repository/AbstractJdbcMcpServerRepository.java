package io.kestra.jdbc.repository;

import java.time.Instant;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.mcp.repositories.McpServerRepositoryInterface;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public abstract class AbstractJdbcMcpServerRepository extends AbstractJdbcCrudRepository<McpServer> implements McpServerRepositoryInterface {
    private final ApplicationEventPublisher<CrudEvent<McpServer>> eventPublisher;
    private final BroadcastQueueInterface<McpServer> mcpQueue;

    public AbstractJdbcMcpServerRepository(io.kestra.jdbc.AbstractJdbcRepository<McpServer> jdbcRepository,
        ApplicationEventPublisher<CrudEvent<McpServer>> eventPublisher,
        BroadcastQueueInterface<McpServer> mcpQueue) {
        super(jdbcRepository);
        this.eventPublisher = eventPublisher;
        this.mcpQueue = mcpQueue;
    }

    @Override
    public boolean exists(String tenantId, String id) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL.using(configuration)
                .fetchExists(jdbcRepository.getTable(),
                    this.defaultFilter(tenantId).and(field("id", String.class).eq(id))));
    }

    @Override
    public Optional<McpServer> get(String tenantId, String id) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                Record record = context
                    .select(VALUE_FIELD)
                    .from(jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId))
                    .and(field("id", String.class).eq(id))
                    .fetchAny();
                return record == null ? Optional.empty() : Optional.of(jdbcRepository.map(record));
            });
    }

    @Override
    public ArrayListTotal<McpServer> listAll(Pageable pageable, String tenantId) {
        return findPage(pageable, tenantId, DSL.noCondition());
    }

    @Override
    public McpServer save(McpServer previousMcpServer, McpServer mcpServer) {
        if (previousMcpServer != null && previousMcpServer.equals(mcpServer)) {
            return previousMcpServer;
        }

        Instant now = Instant.now();
        Instant created = previousMcpServer == null ? now : previousMcpServer.created();
        McpServer toSave = mcpServer.withTimestamps(created, now);

        this.jdbcRepository.persist(toSave);
        this.eventPublisher.publishEvent(CrudEvent.of(previousMcpServer, toSave));
        try {
            this.mcpQueue.emit(toSave);
        } catch (QueueException e) {
            log.warn("Failed to emit MCP server update to queue", e);
        }

        return toSave;
    }

    @Override
    public Optional<McpServer> delete(String tenantId, String id) {
        Optional<McpServer> mcpServer = this.get(tenantId, id);
        if (mcpServer.isEmpty()) {
            return Optional.empty();
        }

        McpServer deleted = mcpServer.get().toDeleted();
        this.jdbcRepository.persist(deleted);
        this.eventPublisher.publishEvent(CrudEvent.delete(mcpServer.get()));
        try {
            this.mcpQueue.emit(deleted);
        } catch (QueueException e) {
            log.warn("Failed to emit MCP server deletion to queue", e);
        }

        return Optional.of(deleted);
    }
}
