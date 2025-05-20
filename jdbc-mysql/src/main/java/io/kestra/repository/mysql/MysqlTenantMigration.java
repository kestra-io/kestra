package io.kestra.repository.mysql;

import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcTenantMigration;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jooq.Table;

@Singleton
@MysqlRepositoryEnabled
public class MysqlTenantMigration extends AbstractJdbcTenantMigration {

    protected MysqlTenantMigration(JooqDSLContextWrapper dslContextWrapper) {
        super(dslContextWrapper);
    }

    @Override
    protected int updateTenantId(Table<?> table, DSLContext context) {
        String query = "UPDATE `" + table.getName() + "` " +
            "SET `value` = JSON_SET(`value`, '$.tenantId', ?) " +
            "WHERE JSON_UNQUOTE(JSON_EXTRACT(`value`, '$.tenantId')) IS NULL";

        return context.execute(query, "main");
    }
}
