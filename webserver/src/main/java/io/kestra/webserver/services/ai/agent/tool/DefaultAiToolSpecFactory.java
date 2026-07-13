package io.kestra.webserver.services.ai.agent.tool;

import java.lang.reflect.Method;

import dev.langchain4j.agent.tool.ToolSpecification;
import jakarta.inject.Singleton;

/**
 * Default spec factory: hides {@link TenantId} parameters from the model, matching the single-tenant
 * OSS surface. An edition with multiple tenants replaces this to expose them.
 */
@Singleton
public class DefaultAiToolSpecFactory implements AiToolSpecFactory {
    @Override
    public ToolSpecification specificationFrom(final Method method) {
        return AiToolSpecifications.toolSpecificationFrom(method, false);
    }
}
