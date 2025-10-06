package io.kestra.core.runners.pebble.functions;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

import io.kestra.core.utils.IdUtils;
import java.util.Map;

public class FunctionTestUtils {

    public static final String NAMESPACE = "io.kestra.tests";

    public static Map<String, Object> getVariables() {
        return getVariables(NAMESPACE);
    }

    public static Map<String, Object> getVariables(String namespace) {
        return getVariables(MAIN_TENANT, namespace);
    }

    public static Map<String, Object> getVariables(String tenantId, String namespace) {
        return Map.of(
            "flow", Map.of(
                "id", "kv",
                "tenantId", tenantId,
                "namespace", namespace)
        );
    }

    public static Map<String, Object> getVariablesWithExecution(String namespace) {
        return getVariablesWithExecution(namespace, IdUtils.create());
    }

    public static Map<String, Object> getVariablesWithExecution(String namespace, String executionId) {
        return Map.of(
            "flow", Map.of(
                "id", "flow",
                "namespace", namespace,
                "tenantId", MAIN_TENANT),
            "execution", Map.of("id", executionId)
        );
    }

}
