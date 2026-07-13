package io.kestra.webserver.services.ai.agent.tool;

import java.lang.reflect.Method;

import dev.langchain4j.agent.tool.ToolSpecification;

/**
 * Builds the model-facing {@link ToolSpecification} for a tool's {@code @Tool} method. This is the
 * extension point for edition-specific schema shaping: the default hides {@link TenantId}
 * parameters — a single-tenant surface has nothing to target — while an edition that supports
 * multiple tenants replaces this to expose them. The catalog depends only on this interface, so it
 * carries no knowledge of who exposes what.
 */
public interface AiToolSpecFactory {
    ToolSpecification specificationFrom(Method method);
}
