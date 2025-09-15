package io.kestra.repository.postgres;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcTenantMigration;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.Table;

@Singleton
@PostgresRepositoryEnabled
public class PostgresTenantMigration extends AbstractJdbcTenantMigration {

    protected PostgresTenantMigration(
        JooqDSLContextWrapper dslContextWrapper) {
        super(dslContextWrapper);
    }

    @Override
    protected int updateTenantIdField(Table<?> table, DSLContext context) {
        String query = "UPDATE " + table.getQualifiedName() + " " +
            "SET value = jsonb_set(value, '{tenantId}', ?::jsonb) " +
            "WHERE (value->>'tenantId') IS NULL";

        return context.execute(query, "\"" + MAIN_TENANT + "\"");
    }

    @Override
    protected int updateTenantIdFieldAndKey(Table<?> table, DSLContext context) {
        String query = """
            UPDATE %s
            SET
                key = ? || '_' || key,
                value = jsonb_set(value, '{tenantId}', to_jsonb(?::text))
            WHERE (value->>'tenantId') IS NULL
        """.formatted(table.getQualifiedName());

        return context.execute(query, MAIN_TENANT, MAIN_TENANT);
    }
}
