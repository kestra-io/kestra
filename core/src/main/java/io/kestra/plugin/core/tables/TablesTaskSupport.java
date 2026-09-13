package io.kestra.plugin.core.tables;

import io.kestra.core.contexts.configuration.SystemFlowsConfiguration;
import io.kestra.core.runners.RunContext;
import io.kestra.fethr.table.RowService;
import io.kestra.fethr.table.TableServices;

/**
 * The plumbing {@link Rows} needs, kept out of {@code run()} so the task stays about its actions.
 *
 * <p>
 * Tables are tenant-scoped under the system namespace -- the same coordinates the REST controllers
 * use -- so there is deliberately no namespace property on the task.
 */
final class TablesTaskSupport {

    private TablesTaskSupport() {
    }

    /**
     * Resolves the row service from the run context.
     *
     * <p>
     * Tables exist only on a PostgreSQL-backed deployment; on H2 the bean is simply absent. Saying so
     * is better than a raw no-such-bean failure mid-execution, which tells a flow author nothing
     * about what to change.
     */
    static RowService rowService(RunContext runContext) {
        return TableServices.rowService()
            .orElseThrow(
                () -> new IllegalArgumentException(
                    "Table tasks require a Postgres-backed Kestra deployment (kestra.repository.type=postgres)"
                )
            );
    }

    static String tenantId(RunContext runContext) {
        return runContext.flowInfo().tenantId();
    }

    static String namespace() {
        return SystemFlowsConfiguration.DEFAULT_NAMESPACE;
    }
}
