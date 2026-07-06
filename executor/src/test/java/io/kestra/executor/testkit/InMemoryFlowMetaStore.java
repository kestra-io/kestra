package io.kestra.executor.testkit;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.runners.FlowMetaStoreInterface;

/**
 * Map-backed {@link FlowMetaStoreInterface}. Flows must be registered up-front with
 * {@link #register(FlowWithSource)}; they are returned as-is (no plugin-defaults injection).
 */
public class InMemoryFlowMetaStore implements FlowMetaStoreInterface {
    private final Map<String, FlowWithSource> flows = new ConcurrentHashMap<>();

    public void register(FlowWithSource flow) {
        flows.put(key(flow.getTenantId(), flow.getNamespace(), flow.getId()), flow);
    }

    @Override
    public boolean isNamespaceExists(String tenant, String namespace) {
        return flows.values().stream()
            .anyMatch(flow -> flow.getTenantId().equals(tenant) && flow.getNamespace().startsWith(namespace));
    }

    @Override
    public Collection<FlowWithSource> allLastVersion() {
        return flows.values();
    }

    @Override
    public Optional<FlowInterface> findById(String tenantId, String namespace, String id, Optional<Integer> revision) {
        return Optional.ofNullable(flows.get(key(tenantId, namespace, id)));
    }

    @Override
    public Optional<FlowInterface> findByExecution(Execution execution) {
        // unlike the interface default, does not require a flow revision on the execution:
        // fixture flows are registered as single latest versions
        return Optional.ofNullable(flows.get(key(execution.getTenantId(), execution.getNamespace(), execution.getFlowId())));
    }

    @Override
    public Optional<FlowWithSource> findByExecutionThenInjectDefaults(Execution execution) {
        return Optional.ofNullable(flows.get(key(execution.getTenantId(), execution.getNamespace(), execution.getFlowId())));
    }

    private static String key(String tenantId, String namespace, String id) {
        return tenantId + "|" + namespace + "|" + id;
    }
}
