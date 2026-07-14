package io.kestra.webserver.services.ai.agent.tool;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.AgentToolCall;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * The single source of truth for what the agent can do, and the single dispatch point both front
 * doors converge on. Each {@link AiPlatformTool} is reflected once — name/description/input schema
 * derived from its {@code @Tool}-annotated method — and merged with the docs MCP tools. AgentMode
 * profiles select a subset of these specs per turn; every call is routed back through
 * {@link #dispatch} for execution.
 *
 * <p>
 * A tenant-scoped tool declares a {@link TenantId} parameter whose visibility in the model-facing
 * spec is decided by the {@link AiToolSpecFactory} (hidden by default, exposed by editions with
 * multiple tenants). OSS tools carry no authorization; EE {@code @Replaces} subclasses override the
 * same {@code @Tool} method to validate the caller's grants and then delegate to {@code super}.
 * Because such an override drops the {@code @Tool}/{@code @P} annotations, {@link #toolMethod}
 * resolves the annotated method up the class hierarchy — the spec comes from the OSS method while
 * execution dispatches virtually to the EE override.
 * </p>
 */
@Singleton
@Slf4j
public class ToolCatalog {
    private final List<AiPlatformTool> platformTools;
    private final List<AiAuthoringTool> authoringTools;
    private final DocsMcpToolProvider docsMcpToolProvider;
    private final AgentToolPermissionEvaluator permissionEvaluator;
    private final AiToolSpecFactory specFactory;

    private volatile Map<String, ToolEntry> registry;

    @Inject
    public ToolCatalog(
        final List<AiPlatformTool> platformTools,
        final List<AiAuthoringTool> authoringTools,
        final DocsMcpToolProvider docsMcpToolProvider,
        final AgentToolPermissionEvaluator permissionEvaluator,
        final AiToolSpecFactory specFactory) {
        this.platformTools = platformTools;
        this.authoringTools = authoringTools;
        this.docsMcpToolProvider = docsMcpToolProvider;
        this.permissionEvaluator = permissionEvaluator;
        this.specFactory = specFactory;
    }

    public record ToolEntry(
        String name,
        ToolSpecification specification,
        ToolExecutor executor,
        AgentToolCall.Kind kind,
        @Nullable AgentToolFamily family,
        AgentWritePolicy writePolicy,
        @Nullable AiTool tool) {
        /** Docs MCP entries carry no tool bean and are outside permission evaluation. */
        public boolean isPermissionEvaluated() {
            return tool != null;
        }

        /** Authoring tools are non-mutating drafts, advertised in every mode. */
        public boolean isAuthoring() {
            return kind == AgentToolCall.Kind.AUTHORING;
        }
    }

    private Map<String, ToolEntry> registry() {
        if (registry != null) {
            return registry;
        }
        synchronized (this) {
            if (registry != null) {
                return registry;
            }
            Map<String, ToolEntry> built = new LinkedHashMap<>();

            for (AiPlatformTool tool : platformTools) {
                register(built, tool, AgentToolCall.Kind.PLATFORM, tool.family(), tool.writePolicy());
            }
            // Authoring tools are drafts-only: no family (they escape mode gating) and never confirmed.
            for (AiAuthoringTool tool : authoringTools) {
                register(built, tool, AgentToolCall.Kind.AUTHORING, null, AgentWritePolicy.AUTO);
            }

            // Docs MCP tools are public documentation reads: no permission and no tenant scoping.
            docsMcpToolProvider.tools().forEach(
                (spec, executor) -> built.put(
                    spec.name(), new ToolEntry(
                        spec.name(), spec, executor, AgentToolCall.Kind.PLATFORM, AgentToolFamily.READ, AgentWritePolicy.AUTO, null
                    )
                )
            );

            this.registry = built;
            return built;
        }
    }

    private void register(final Map<String, ToolEntry> built, final AiTool tool,
        final AgentToolCall.Kind kind, @Nullable final AgentToolFamily family,
        final AgentWritePolicy writePolicy) {
        Method method = toolMethod(tool.toolInstance());

        boolean hasQueryFilter = Arrays.stream(method.getParameters())
            .anyMatch(parameter -> parameter.isAnnotationPresent(QueryFilterFormat.class));
        ToolSpecification spec = specFactory.specificationFrom(method);

        // propagateToolExecutionExceptions: let a @Tool throw propagate out of dispatch (instead of
        // langchain4j swallowing it into an opaque "ok" result text) so AgentOrchestrator can record it
        // as an error outcome and feed the message back to the model. QueryFilterToolExecutor already
        // propagates, so both executor paths behave the same on failure.
        ToolExecutor executor = hasQueryFilter
            ? new QueryFilterToolExecutor(tool.toolInstance(), method)
            : DefaultToolExecutor.builder()
                .object(tool.toolInstance())
                .originalMethod(method)
                .methodToInvoke(method)
                .propagateToolExecutionExceptions(true)
                .build();
        built.put(spec.name(), new ToolEntry(spec.name(), spec, executor, kind, family, writePolicy, tool));
    }

    public Collection<ToolEntry> entries() {
        return registry().values();
    }

    public Optional<ToolEntry> byName(final String name) {
        return Optional.ofNullable(registry().get(name));
    }

    /**
     * Execute a tool call scoped to the given caller context — the one entry point both the in-process
     * loop and (later) the MCP projection use. Enforces the coarse tool permission against the
     * caller's tenant, binds the context to the executor thread so {@code @Tool} methods (and EE
     * overrides) can read it, and always clears it. Per-namespace and cross-tenant checks are the EE
     * tool subclasses' responsibility.
     *
     * @param request the tool call emitted by the model
     * @param context what the call runs as (tenant, principal, provider, draft channel)
     * @return the tool's textual result
     * @throws IllegalArgumentException if the tool name is unknown
     * @throws ToolPermissionDeniedException if the caller lacks the tool's permission
     */
    public String dispatch(final ToolExecutionRequest request, final AgentCallContext.Context context) {
        ToolEntry entry = registry().get(request.name());
        if (entry == null) {
            throw new IllegalArgumentException("Unknown tool: '" + request.name() + "'");
        }

        if (entry.isPermissionEvaluated()) {
            // The enforcement point: every front door (in-process loop, later the MCP projection) runs
            // through here, acting as the caller. The ModeProfiles pre-filter is UX only.
            if (!permissionEvaluator.isAllowed(entry, context.tenant(), context.principal())) {
                throw new ToolPermissionDeniedException(entry.name(), context.tenant());
            }
        }

        AgentCallContext.set(context);
        try {
            return entry.executor().execute(request, context.tenant());
        } finally {
            AgentCallContext.clear();
        }
    }

    /**
     * The {@code @Tool}-annotated method, searched up the class hierarchy: an EE {@code @Replaces}
     * subclass overrides the method without repeating {@code @Tool}/{@code @P}, so the annotated
     * definition lives on the OSS super-class while the bean itself is the EE type. Executing the
     * returned method against the bean dispatches virtually to the override.
     */
    private static Method toolMethod(final Object toolInstance) {
        for (Class<?> type = toolInstance.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    return method;
                }
            }
        }
        throw new IllegalStateException(
            "No @Tool-annotated method found on " + toolInstance.getClass().getName()
        );
    }
}
