package io.kestra.core.trace;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.RunContext;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;

import java.util.Map;

public final class TraceUtils {
    public static final AttributeKey<String> ATTR_UID = AttributeKey.stringKey("kestra.uid");

    private static final AttributeKey<String> ATTR_TENANT_ID = AttributeKey.stringKey("kestra.tenantId");
    private static final AttributeKey<String> ATTR_NAMESPACE = AttributeKey.stringKey("kestra.namespace");
    private static final AttributeKey<String> ATTR_FLOW_ID = AttributeKey.stringKey("kestra.flowId");
    private static final AttributeKey<String> ATTR_EXECUTION_ID = AttributeKey.stringKey("kestra.executionId");

    public static final AttributeKey<String> ATTR_SOURCE = AttributeKey.stringKey("kestra.source");

    private TraceUtils() {}

    public static Attributes attributesFrom(Execution execution) {
        var builder = Attributes.builder()
            .put(ATTR_NAMESPACE, execution.getNamespace())
            .put(ATTR_FLOW_ID, execution.getFlowId())
            .put(ATTR_EXECUTION_ID, execution.getId());

        if (execution.getTenantId() != null) {
            builder.put(ATTR_TENANT_ID, execution.getTenantId());
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    public static Attributes attributesFrom(RunContext runContext) {
        var flowInfo = runContext.flowInfo();
        var execution = (Map<String, Object>) runContext.getVariables().get("execution");
        var executionId = (String) execution.get("id");

        var builder = Attributes.builder()
            .put(ATTR_NAMESPACE, flowInfo.namespace())
            .put(ATTR_FLOW_ID, flowInfo.id())
            .put(ATTR_EXECUTION_ID, executionId);

        if (flowInfo.tenantId() != null) {
            builder.put(ATTR_TENANT_ID, flowInfo.tenantId());
        }

        return builder.build();
    }

    public static Attributes attributesFrom(Class<?> clazz) {
        return Attributes.builder().put(ATTR_SOURCE, clazz.getName()).build();
    }
}
