package io.kestra.repository.h2;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcTenantMigration;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.Table;

@Singleton
@H2RepositoryEnabled
public class H2TenantMigration extends AbstractJdbcTenantMigration {

    protected H2TenantMigration(JooqDSLContextWrapper dslContextWrapper) {
        super(dslContextWrapper);
    }

    @Override
    protected int updateTenantIdField(Table<?> table, DSLContext context) {
        String query = """
            UPDATE "%s"
            SET "value" = '{"tenantId":"%s",' || SUBSTRING("value", 2)
            WHERE JQ_STRING("value", '.tenantId') IS NULL
        """.formatted(table.getName(), "main");

        return context.execute(query, "main");
    }

    @Override
    protected int updateTenantIdFieldAndKey(Table<?> table, DSLContext context) {
        String query = """
            UPDATE "%s"
            SET
                "key" = '%s_' || "key",
                "value" = '{"tenantId":"%s",' || SUBSTRING("value", 2)
            WHERE JQ_STRING("value", '.tenantId') IS NULL
        """.formatted(table.getName(), MAIN_TENANT, MAIN_TENANT);

        return context.execute(query);
    }

    @Override
    protected int deleteTutorialFlows(Table<?> table, DSLContext context) {
        String query = """
            DELETE FROM "%s"
            WHERE JQ_STRING("value", '.namespace') = ?
        """.formatted(table.getName());
        return context.execute(query, "tutorial");
    }
}
