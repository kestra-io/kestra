package io.kestra.repository.h2;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.server.ClusterEvent;
import io.kestra.jdbc.repository.AbstractJdbcMcpServerRepository;

import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2McpServerRepository extends AbstractJdbcMcpServerRepository {
    @Inject
    public H2McpServerRepository(@Named("mcp") H2Repository<McpServer> repository,
        ApplicationEventPublisher<CrudEvent<McpServer>> eventPublisher,
        BroadcastQueueInterface<ClusterEvent> clusterEventQueue) {
        super(repository, eventPublisher, clusterEventQueue);
    }
}
