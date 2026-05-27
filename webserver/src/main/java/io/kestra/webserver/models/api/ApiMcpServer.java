package io.kestra.webserver.models.api;

import io.kestra.core.mcp.models.McpServer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

/**
 * API DTO for MCP server creation, update, and retrieval.
 * <p>
 * Decouples the public API contract from the internal {@link McpServer} domain object.
 * Read-only fields ({@code isDefault}, {@code created}, {@code updated}) are
 * populated in responses and ignored in request bodies.
 */
public record ApiMcpServer(
    @Schema(description = "Unique identifier of the MCP server.")
    @NotNull @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    String id,

    @Schema(description = "Human-readable description of the MCP server.")
    String description,

    @Schema(description = "Instructions sent to the AI model when using this server.")
    String instructions,

    @Schema(description = "Visibility of the server.")
    McpServer.ServerType serverType,

    @Schema(description = "Authentication type for private servers.")
    McpServer.AuthType authType,

    @Schema(description = "Whether the MCP server is disabled.")
    boolean disabled,

    @Schema(description = "Whether this is the default MCP server, auto-provisioned per tenant.", accessMode = Schema.AccessMode.READ_ONLY)
    boolean isDefault,

    @Schema(description = "Timestamp when the server was created.", accessMode = Schema.AccessMode.READ_ONLY)
    Instant created,

    @Schema(description = "Timestamp when the server was last updated.", accessMode = Schema.AccessMode.READ_ONLY)
    Instant updated
) {
    /**
     * Creates an {@link ApiMcpServer} response DTO from a domain {@link McpServer}.
     *
     * @param mcpServer the domain object
     * @return the corresponding API DTO
     */
    public static ApiMcpServer from(final McpServer mcpServer) {
        return new ApiMcpServer(
            mcpServer.id(),
            mcpServer.description(),
            mcpServer.instructions(),
            mcpServer.serverType(),
            mcpServer.authType(),
            mcpServer.disabled(),
            mcpServer.isDefault(),
            mcpServer.created(),
            mcpServer.updated()
        );
    }
}
