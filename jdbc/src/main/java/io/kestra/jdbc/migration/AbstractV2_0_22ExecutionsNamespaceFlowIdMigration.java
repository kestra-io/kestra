package io.kestra.jdbc.migration;

/**
 * Abstract base for the 2.0.22 executions namespace/flow_id index merge migration.
 *
 * <p>
 * Replaces the two overlapping indexes {@code (deleted, tenant_id, namespace)} and
 * {@code (deleted, tenant_id, flow_id)} on the {@code executions} table with a single composite
 * {@code (deleted, tenant_id, namespace, flow_id)} index. Since a flow always belongs to a
 * namespace, the merged index still serves namespace-only queries through its leftmost prefix,
 * serves namespace + flow_id queries with a full four-column seek, and removes one index from the
 * hottest write table.
 */
public abstract class AbstractV2_0_22ExecutionsNamespaceFlowIdMigration extends AbstractSQLMigrationScript {

    @Override
    public String scriptId() {
        return "2.0.22-executions-namespace-flow-id";
    }

    @Override
    public String description() {
        return "Executions: merge namespace and flow_id indexes";
    }
}
