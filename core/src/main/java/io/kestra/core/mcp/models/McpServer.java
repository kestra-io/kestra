package io.kestra.core.mcp.models;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.utils.Enums;
import io.kestra.core.utils.IdUtils;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Represents an MCP (Model Context Protocol) server configuration.
 */
public record McpServer(
    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    String tenantId,

    @NotNull
    @NotBlank
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    String id,

    String description,

    String instructions,

    ServerType serverType,

    AuthType authType,

    String oauthProvider,

    List<String> oauthScopesSupported,

    boolean disabled,

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    boolean isDefault,

    @Hidden
    boolean deleted,

    @Hidden
    Instant created,

    @Hidden
    Instant updated
) implements HasUID, SoftDeletable<McpServer>, BroadcastEvent {

    /** The well-known id of the default MCP server, auto-provisioned per tenant. */
    public static final String DEFAULT_ID = "default";

    /**
     * Controls the visibility of the MCP server.
     */
    public enum ServerType {
        PRIVATE,
        PUBLIC;

        @JsonCreator
        public static ServerType fromString(final String value) {
            return Enums.getForNameIgnoreCase(value, ServerType.class);
        }
    }

    /**
     * Authentication type for private MCP servers.
     * Only relevant when {@link ServerType} is {@link ServerType#PRIVATE}.
     * {@link #OAUTH} and {@link #API_TOKEN} are Enterprise Edition only.
     */
    public enum AuthType {
        BASIC,
        API_TOKEN,
        OAUTH;

        @JsonCreator
        public static AuthType fromString(final String value) {
            return Enums.getForNameIgnoreCase(value, AuthType.class);
        }
    }

    /**
     * Applies defaults for {@code serverType} and {@code authType}, and computes the read-only {@code isDefault} flag.
     */
    public McpServer {
        if (serverType == null) {
            serverType = ServerType.PRIVATE;
        }
        if (authType == null) {
            authType = AuthType.BASIC;
        }
        isDefault = DEFAULT_ID.equals(id);
    }

    /** {@inheritDoc} */
    @Override
    @JsonIgnore
    public String key() {
        return uid();
    }

    /** {@inheritDoc} */
    @Override
    @JsonIgnore
    public String uid() {
        return IdUtils.fromParts(tenantId, id);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDeleted() {
        return deleted;
    }

    /** {@inheritDoc} */
    @Override
    public McpServer toDeleted() {
        return new McpServer(tenantId, id, description, instructions,
            serverType, authType, oauthProvider, oauthScopesSupported, disabled, isDefault, true, created, updated);
    }

    public McpServer withTimestamps(Instant created, Instant updated) {
        return new McpServer(tenantId, id, description, instructions,
            serverType, authType, oauthProvider, oauthScopesSupported, disabled, isDefault, deleted, created, updated);
    }
}
