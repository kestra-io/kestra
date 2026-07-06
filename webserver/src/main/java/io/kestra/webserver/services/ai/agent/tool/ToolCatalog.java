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
import io.kestra.webserver.services.ai.agent.domain.ToolFamily;
import io.kestra.webserver.services.ai.agent.domain.WritePolicy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * The single source of truth for what the agent can do, and the single dispatch point both front
 * doors converge on. Each {@link AiPlatformTool} is reflected once — name/description/input schema
 * derived from its {@code @Tool}-annotated method — and merged with the docs MCP tools. Mode
 * profiles select a subset of these specs per turn; every call is routed back through
 * {@link #dispatch} for execution.
 */
@Singleton
@Slf4j
public class ToolCatalog {
    private final List<AiPlatformTool> platformTools;
    private final DocsMcpToolProvider docsMcpToolProvider;

    private volatile Map<String, ToolEntry> registry;

    @Inject
    public ToolCatalog(final List<AiPlatformTool> platformTools, final DocsMcpToolProvider docsMcpToolProvider) {
        this.platformTools = platformTools;
        this.docsMcpToolProvider = docsMcpToolProvider;
    }

    public record ToolEntry(
        String name,
        ToolSpecification specification,
        ToolExecutor executor,
        ToolFamily family,
        WritePolicy writePolicy
    ) {
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
                Method method = toolMethod(tool.toolInstance());
                // A tool with a @QueryFilterFormat parameter needs its List<QueryFilter> expanded per
                // field (schema) and reassembled from the model's per-field args (execution).
                boolean hasQueryFilter = Arrays.stream(method.getParameters())
                    .anyMatch(parameter -> parameter.isAnnotationPresent(QueryFilterFormat.class));
                ToolSpecification spec = hasQueryFilter
                    ? AiToolSpecifications.toolSpecificationFrom(method)
                    : ToolSpecifications.toolSpecificationFrom(method);
                ToolExecutor executor = hasQueryFilter
                    ? new QueryFilterToolExecutor(tool.toolInstance(), method)
                    : new DefaultToolExecutor(tool.toolInstance(), method);
                built.put(spec.name(), new ToolEntry(
                    spec.name(), spec, executor, tool.family(), tool.writePolicy()
                ));
            }

            docsMcpToolProvider.tools().forEach((spec, executor) ->
                built.put(spec.name(), new ToolEntry(
                    spec.name(), spec, executor, ToolFamily.READ, WritePolicy.AUTO
                ))
            );

            this.registry = built;
            return built;
        }
    }

    public Collection<ToolEntry> entries() {
        return registry().values();
    }

    public Optional<ToolEntry> byName(final String name) {
        return Optional.ofNullable(registry().get(name));
    }

    /**
     * Execute a tool call scoped to the given tenant — the one entry point both the in-process loop
     * and (later) the MCP projection use. Binds the tenant to the executor thread so {@code @Tool}
     * methods can read it, then always clears it.
     *
     * @param request the tool call emitted by the model
     * @param tenant  the tenant the call runs against
     * @return the tool's textual result
     * @throws IllegalArgumentException if the tool name is unknown
     */
    public String dispatch(final ToolExecutionRequest request, final String tenant) {
        ToolEntry entry = registry().get(request.name());
        if (entry == null) {
            throw new IllegalArgumentException("Unknown tool: '" + request.name() + "'");
        }
        AgentCallContext.set(tenant);
        try {
            return entry.executor().execute(request, tenant);
        } finally {
            AgentCallContext.clear();
        }
    }

    private static Method toolMethod(final Object toolInstance) {
        for (Method method : toolInstance.getClass().getMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                return method;
            }
        }
        throw new IllegalStateException(
            "No @Tool-annotated method found on " + toolInstance.getClass().getName()
        );
    }
}
