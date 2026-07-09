package io.kestra.core.runners;

import java.util.Objects;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.FlowInterface;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

/**
 * A concurrency limit definition together with the scope it applies to.
 * <p>
 * A flow-scoped limit comes from the flow's own {@code concurrency} property; namespace and
 * tenant scoped limits are an EE feature. The running counter of a scope is keyed by
 * {@link #uid()}: for the {@link Scope#FLOW} scope it matches {@link ConcurrencyLimit#uid()},
 * for broader scopes the absent parts are kept empty.
 *
 * @param scope the level this limit applies to
 * @param tenantId the tenant, never null
 * @param namespace the namespace the limit applies to, null for a tenant-scoped limit
 * @param flowId the flow the limit applies to, null unless the scope is {@link Scope#FLOW}
 * @param concurrency the limit and its behavior
 */
public record ScopedConcurrencyLimit(
    @NotNull Scope scope,
    @NotNull String tenantId,
    @Nullable String namespace,
    @Nullable String flowId,
    @NotNull Concurrency concurrency) implements HasUID {

    public enum Scope {
        FLOW,
        NAMESPACE,
        TENANT
    }

    public static ScopedConcurrencyLimit ofFlow(FlowInterface flow) {
        return new ScopedConcurrencyLimit(Scope.FLOW, flow.getTenantId(), flow.getNamespace(), flow.getId(), flow.getConcurrency());
    }

    public static ScopedConcurrencyLimit ofNamespace(String tenantId, String namespace, Concurrency concurrency) {
        return new ScopedConcurrencyLimit(Scope.NAMESPACE, tenantId, namespace, null, concurrency);
    }

    public static ScopedConcurrencyLimit ofTenant(String tenantId, Concurrency concurrency) {
        return new ScopedConcurrencyLimit(Scope.TENANT, tenantId, null, null, concurrency);
    }

    /**
     * Whether the given execution coordinates fall inside this scope: the flow itself for a
     * flow scope, the namespace or any descendant for a namespace scope, the whole tenant for
     * a tenant scope.
     */
    public boolean covers(String tenantId, String namespace, String flowId) {
        if (!Objects.equals(this.tenantId, tenantId)) {
            return false;
        }
        return switch (this.scope) {
            case FLOW -> Objects.equals(this.namespace, namespace) && Objects.equals(this.flowId, flowId);
            case NAMESPACE -> Objects.equals(this.namespace, namespace) || (namespace != null && namespace.startsWith(this.namespace + "."));
            case TENANT -> true;
        };
    }

    /** {@inheritDoc} */
    @Override
    public String uid() {
        return this.tenantId + "|" + Objects.requireNonNullElse(this.namespace, "") + "|" + Objects.requireNonNullElse(this.flowId, "");
    }
}
