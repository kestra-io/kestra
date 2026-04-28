package io.kestra.repository.postgres;

import org.jooq.DSLContext;
import org.jooq.Table;
import org.jooq.impl.DSL;

import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcTenantMigration;

import jakarta.inject.Singleton;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

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

    @Override
    protected String selectExecutionsQuery() {
        return "SELECT key, value FROM executions LIMIT ? OFFSET ?";
    }

    @Override
    protected String updateExecutionQuery() {
        return "UPDATE executions SET value = ? WHERE key = ?";
    }

    @Override
    protected int updateExecutionTaskRunsTenantId() {
        return dslContextWrapper.transactionResult(configuration -> {
            DSLContext context = DSL.using(configuration);
            String query = """
                UPDATE executions
                SET value = jsonb_set(value, '{taskRunList}',
                    (SELECT jsonb_agg(elem || jsonb_build_object('tenantId', value->>'tenantId'))
                     FROM jsonb_array_elements(value->'taskRunList') AS elem)
                )
                WHERE value->'taskRunList' IS NOT NULL
                  AND jsonb_array_length(value->'taskRunList') > 0
                  AND EXISTS (
                    SELECT 1 FROM jsonb_array_elements(value->'taskRunList') AS elem
                    WHERE (elem->>'tenantId') IS NULL
                  )
                """;
            return context.execute(query);
        });
    }

    @Override
    protected int countExecutionsWithMissingTaskRunTenantId() {
        return dslContextWrapper.transactionResult(configuration -> {
            DSLContext context = DSL.using(configuration);
            String query = """
                SELECT COUNT(*) FROM executions
                WHERE value->'taskRunList' IS NOT NULL
                  AND jsonb_array_length(value->'taskRunList') > 0
                  AND EXISTS (
                    SELECT 1 FROM jsonb_array_elements(value->'taskRunList') AS elem
                    WHERE (elem->>'tenantId') IS NULL
                  )
                """;
            return context.fetchOne(query).get(0, int.class);
        });
    }
}
