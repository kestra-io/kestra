package io.kestra.core.context;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public class TestRunContextFactory extends RunContextFactory {

    @VisibleForTesting
    public RunContext of() {
        return of("id", "namespace");
    }

    @VisibleForTesting
    public RunContext of(String namespace) {
        return of("id", namespace);
    }

    @VisibleForTesting
    public RunContext of(String id, String namespace) {
        return of(id, namespace, MAIN_TENANT);
    }

    @VisibleForTesting
    public RunContext of(String id, String namespace, String tenantId) {
        return of(Map.of("flow", Map.of("id", id, "namespace", namespace, "tenantId", tenantId)));
    }

    @VisibleForTesting
    public RunContext of(String namespace, Map<String, Object> inputs) {
        return of(namespace, MAIN_TENANT, inputs);
    }

    @VisibleForTesting
    public RunContext of(String namespace, String tenantId, Map<String, Object> inputs) {
        Map<String, Object> variables = new java.util.HashMap<>(Map.of("flow",
            Map.of("id", "id", "namespace", namespace, "tenantId", tenantId)));
        variables.putAll(inputs);
        return of(variables);
    }
}
