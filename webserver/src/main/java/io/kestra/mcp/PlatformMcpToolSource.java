package io.kestra.mcp;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.ai.agent.models.AgentPrincipal;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.AgentPrincipalResolver;
import io.kestra.webserver.services.ai.agent.tool.AgentToolPermissionEvaluator;
import io.kestra.webserver.services.ai.agent.tool.McpPlatformTool;
import io.kestra.webserver.services.ai.agent.tool.ToolCatalog;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.JsonSchemaElementJsonUtils;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * The {@link McpToolSource.Kind#PLATFORM} tool source: exposes every {@link McpPlatformTool} of the
 * {@link ToolCatalog} across tenants, with an extra {@value #TENANT_ARGUMENT} argument naming the tenant a call
 * acts on.
 * <p>
 * A caller sees the tools they may use in at least one tenant the server allows, and every call runs through
 * {@link ToolCatalog#dispatch} as that caller in the requested tenant, where the permission checks apply.
 */
@Slf4j
@Singleton
@Requires(property = "kestra.mcp.platform-server.enabled", value = "true")
public class PlatformMcpToolSource implements McpToolSource {
    static final String TENANT_ARGUMENT = "tenant";

    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final Map<String, Object> TENANT_PROPERTY = Map.of(
        "type", "string",
        "description", "The tenant to act on. Required when you can act on more than one tenant."
    );

    private final ToolCatalog toolCatalog;
    private final AgentToolPermissionEvaluator permissionEvaluator;
    private final AgentPrincipalResolver principalResolver;
    private final PlatformMcpServerConfig config;
    private final TenantService tenantService;

    @Inject
    public PlatformMcpToolSource(
        ToolCatalog toolCatalog,
        AgentToolPermissionEvaluator permissionEvaluator,
        AgentPrincipalResolver principalResolver,
        PlatformMcpServerConfig config,
        TenantService tenantService) {
        this.toolCatalog = toolCatalog;
        this.permissionEvaluator = permissionEvaluator;
        this.principalResolver = principalResolver;
        this.config = config;
        this.tenantService = tenantService;
    }

    @Override
    public Kind kind() {
        return Kind.PLATFORM;
    }

    @Override
    public McpToolView view(KestraMcpTransportContext context) {
        List<String> tenants = tenants(context.getUserId());
        Map<String, AgentPrincipal> principals = new HashMap<>();
        tenants.forEach(tenant -> principals.put(tenant, principalResolver.resolve(context.getUserId(), tenant)));

        List<ToolCatalog.ToolEntry> allowed = toolCatalog.entries().stream()
            .filter(entry -> entry.tool() instanceof McpPlatformTool)
            .filter(entry -> tenants.stream().anyMatch(tenant -> permissionEvaluator.isAllowed(entry, tenant, principals.get(tenant))))
            .sorted(Comparator.comparing(ToolCatalog.ToolEntry::name))
            .toList();
        return new PlatformToolView(this, allowed);
    }

    private McpServerFeatures.AsyncToolSpecification toolSpecification(ToolCatalog.ToolEntry entry) {
        McpSchema.Tool tool = McpSchema.Tool.builder()
            .name(entry.name())
            .description(entry.specification().description())
            .inputSchema(inputSchema(entry.specification()))
            .annotations(McpToolAnnotationsMapper.toToolAnnotations(((McpPlatformTool) entry.tool()).annotations()))
            .build();
        return new McpServerFeatures.AsyncToolSpecification(
            tool,
            (exchange, request) -> Mono.fromCallable(() -> call(entry.name(), (KestraMcpTransportContext) exchange.transportContext(), request))
                .subscribeOn(Schedulers.boundedElastic())
        );
    }

    private McpSchema.CallToolResult call(String toolName, KestraMcpTransportContext context, McpSchema.CallToolRequest request) {
        Map<String, Object> arguments = new LinkedHashMap<>(request.arguments() == null ? Map.of() : request.arguments());
        Object requestedTenant = arguments.remove(TENANT_ARGUMENT);
        try {
            String tenant = tenant(requestedTenant, tenants(context.getUserId()));
            AgentCallContext.Context callContext = new AgentCallContext.Context(
                tenant, principalResolver.resolve(context.getUserId(), tenant), null, null
            );
            ToolExecutionRequest execution = ToolExecutionRequest.builder()
                .id(IdUtils.create())
                .name(toolName)
                .arguments(MAPPER.writeValueAsString(arguments))
                .build();
            ToolCatalog.DispatchResult result = tenantService.callInTenant(tenant, () -> toolCatalog.dispatch(execution, callContext));
            return McpSchema.CallToolResult.builder().addTextContent(result.text()).build();
        } catch (JsonProcessingException e) {
            return error("The arguments of tool '%s' could not be read.".formatted(toolName));
        } catch (RuntimeException e) {
            log.debug("Platform MCP tool '{}' failed for caller '{}'", toolName, context.getUserId(), e);
            return error(e.getMessage() != null ? e.getMessage() : "Tool '%s' failed.".formatted(toolName));
        }
    }

    private List<String> tenants(@Nullable String callerId) {
        List<String> reachable = principalResolver.reachableTenants(callerId);
        List<String> configured = ListUtils.emptyOnNull(config.tenants());
        return configured.isEmpty() ? reachable : reachable.stream().filter(configured::contains).toList();
    }

    private static String tenant(@Nullable Object requested, List<String> tenants) {
        if (requested == null || requested.toString().isBlank()) {
            if (tenants.size() == 1) {
                return tenants.getFirst();
            }
            throw new IllegalArgumentException(
                tenants.isEmpty()
                    ? "There is no tenant you can act on through this server."
                    : "Pass the tenant to act on, one of: %s.".formatted(String.join(", ", tenants))
            );
        }
        String tenant = requested.toString();
        if (!tenants.contains(tenant)) {
            throw new IllegalArgumentException(
                "You cannot act on tenant '%s' through this server. Use one of: %s.".formatted(tenant, String.join(", ", tenants))
            );
        }
        return tenant;
    }

    @SuppressWarnings("unchecked")
    private static McpSchema.JsonSchema inputSchema(ToolSpecification specification) {
        Map<String, Object> schema = specification.parameters() == null ? Map.of() : JsonSchemaElementJsonUtils.toMap(specification.parameters());
        Map<String, Object> properties = new LinkedHashMap<>((Map<String, Object>) schema.getOrDefault("properties", Map.of()));
        properties.put(TENANT_ARGUMENT, TENANT_PROPERTY);
        List<String> required = ((List<Object>) schema.getOrDefault("required", List.of())).stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        return new McpSchema.JsonSchema(
            "object", properties, required, false, (Map<String, Object>) schema.get("$defs"), null
        );
    }

    private static McpSchema.CallToolResult error(String message) {
        return McpSchema.CallToolResult.builder().isError(true).addTextContent(message).build();
    }

    /**
     * Callers allowed the same tools share one built server, whichever tenants they are allowed them in.
     */
    private record PlatformToolView(PlatformMcpToolSource source, List<ToolCatalog.ToolEntry> entries) implements McpToolView {
        @Override
        public String cacheKey() {
            return entries.stream().map(ToolCatalog.ToolEntry::name).collect(Collectors.joining(","));
        }

        @Override
        public List<McpServerFeatures.AsyncToolSpecification> tools() {
            return entries.stream().map(source::toolSpecification).toList();
        }
    }
}
