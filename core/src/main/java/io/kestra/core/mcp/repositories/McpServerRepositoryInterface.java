package io.kestra.core.mcp.repositories;

import java.util.Optional;

import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.repositories.ArrayListTotal;
import io.micronaut.data.model.Pageable;

public interface McpServerRepositoryInterface {

    boolean exists(String tenantId, String id);

    Optional<McpServer> get(String tenantId, String id);

    ArrayListTotal<McpServer> find(Pageable pageable, String tenantId);

    ArrayListTotal<McpServer> findForAllTenants(Pageable pageable);
    
    McpServer save(McpServer previousMcpServer, McpServer mcpServer);

    Optional<McpServer> delete(String tenantId, String id);
}
