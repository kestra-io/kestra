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
 * @param concurrency the limit and its behavior — null only for release-time scopes whose
 *        definition was removed while the admitted execution ran (see {@link #fromUid})
 */
public record ScopedConcurrencyLimit(
    @NotNull Scope scope,
    @NotNull String tenantId,
    @Nullable String namespace,
    @Nullable String flowId,
    @Nullable Concurrency concurrency) implements HasUID {

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
     * Rebuild a scope from its uid — the form persisted on an execution's metadata when it
     * claims its slots. Tenant, namespace and flow ids cannot contain {@code |}, so the parts
     * split back unambiguously; blank parts mark the broader scopes.
     *
     * @param concurrency the currently defined limit of that scope, or null when its
     *        definition was removed since the execution was admitted
     */
    public static ScopedConcurrencyLimit fromUid(String uid, @Nullable Concurrency concurrency) {
        String[] parts = uid.split("\\|", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid concurrency scope uid: " + uid);
        }
        String tenantId = parts[0];
        String namespace = parts[1].isEmpty() ? null : parts[1];
        String flowId = parts[2].isEmpty() ? null : parts[2];
        Scope scope = flowId != null ? Scope.FLOW : namespace != null ? Scope.NAMESPACE : Scope.TENANT;
        return new ScopedConcurrencyLimit(scope, tenantId, namespace, flowId, concurrency);
    }

    /** {@inheritDoc} */
    @Override
    public String uid() {
        return this.tenantId + "|" + Objects.requireNonNullElse(this.namespace, "") + "|" + Objects.requireNonNullElse(this.flowId, "");
    }
}
