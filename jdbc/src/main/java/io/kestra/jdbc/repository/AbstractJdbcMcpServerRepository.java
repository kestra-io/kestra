package io.kestra.jdbc.repository;

import java.time.Instant;
import java.time.LocalDateTime;
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
import io.kestra.core.mcp.models.McpServerClusterEventPayload;
import io.kestra.core.server.ClusterEvent;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public abstract class AbstractJdbcMcpServerRepository extends AbstractJdbcCrudRepository<McpServer> implements McpServerRepositoryInterface {
    private final ApplicationEventPublisher<CrudEvent<McpServer>> eventPublisher;
    private final BroadcastQueueInterface<ClusterEvent> clusterEventQueue;

    public AbstractJdbcMcpServerRepository(io.kestra.jdbc.AbstractJdbcRepository<McpServer> jdbcRepository,
        ApplicationEventPublisher<CrudEvent<McpServer>> eventPublisher,
        BroadcastQueueInterface<ClusterEvent> clusterEventQueue) {
        super(jdbcRepository);
        this.eventPublisher = eventPublisher;
        this.clusterEventQueue = clusterEventQueue;
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
            this.clusterEventQueue.emit(new ClusterEvent(ClusterEvent.EventType.MCP_SERVER_CHANGED, LocalDateTime.now(), mcpServerEventMessage(toSave)));
        } catch (QueueException e) {
            log.warn("Failed to emit MCP server update to cluster event queue", e);
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
            this.clusterEventQueue.emit(new ClusterEvent(ClusterEvent.EventType.MCP_SERVER_CHANGED, LocalDateTime.now(), mcpServerEventMessage(deleted)));
        } catch (QueueException e) {
            log.warn("Failed to emit MCP server deletion to cluster event queue", e);
        }

        return Optional.of(deleted);
    }

    private static String mcpServerEventMessage(McpServer mcpServer) {
        return McpServerClusterEventPayload.of(mcpServer).toJson();
    }
}
