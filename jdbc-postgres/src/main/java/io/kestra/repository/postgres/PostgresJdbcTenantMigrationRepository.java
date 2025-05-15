package io.kestra.repository.postgres;

import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcTenantMigrationRepository;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.Table;

@Singleton
@PostgresRepositoryEnabled
public class PostgresJdbcTenantMigrationRepository extends AbstractJdbcTenantMigrationRepository {

    protected PostgresJdbcTenantMigrationRepository(
        JooqDSLContextWrapper dslContextWrapper) {
        super(dslContextWrapper);
    }

    @Override
    protected int updateTenantId(Table<?> table, DSLContext context) {
        String query = "UPDATE " + table.getQualifiedName() + " " +
            "SET value = jsonb_set(value, '{tenantId}', ?::jsonb) " +
            "WHERE (value->>'tenantId') IS NULL";

        return context.execute(query, "\"main\"");
    }
}
