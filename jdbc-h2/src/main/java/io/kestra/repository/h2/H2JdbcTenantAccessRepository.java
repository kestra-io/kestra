package io.kestra.repository.h2;

import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcTenantMigrationRepository;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.Table;

@Singleton
@H2RepositoryEnabled
public class H2JdbcTenantAccessRepository extends AbstractJdbcTenantMigrationRepository {

    protected H2JdbcTenantAccessRepository(JooqDSLContextWrapper dslContextWrapper) {
        super(dslContextWrapper);
    }

    @Override
    protected int updateTenantId(Table<?> table, DSLContext context) {
        String query = """
            UPDATE "%s"
            SET "value" = '{"tenantId":"%s",' || SUBSTRING("value", 2)
            WHERE JQ_STRING("value", '.tenantId') IS NULL
        """.formatted(table.getName(), "main");

        return context.execute(query, "main");
    }
}
