package io.kestra.repository.mysql;

import org.jooq.DSLContext;
import org.jooq.Table;

import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcTenantMigration;

import jakarta.inject.Singleton;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;

@Singleton
@MysqlRepositoryEnabled
public class MysqlTenantMigration extends AbstractJdbcTenantMigration {

    protected MysqlTenantMigration(JooqDSLContextWrapper dslContextWrapper) {
        super(dslContextWrapper);
    }

    @Override
    protected int updateTenantIdField(Table<?> table, DSLContext context) {
        String query = "UPDATE `" + table.getName() + "` " +
            "SET `value` = JSON_SET(`value`, '$.tenantId', ?) " +
            "WHERE JSON_UNQUOTE(JSON_EXTRACT(`value`, '$.tenantId')) IS NULL";

        return context.execute(query, MAIN_TENANT);
    }

    @Override
    protected int updateTenantIdFieldAndKey(Table<?> table, DSLContext context) {
        String query = """
                UPDATE `%s`
                SET
                    `key` = CONCAT(?, '_', `key`),
                    `value` = JSON_SET(`value`, '$.tenantId', ?)
                WHERE JSON_UNQUOTE(JSON_EXTRACT(`value`, '$.tenantId')) IS NULL
            """.formatted(table.getName());

        return context.execute(query, MAIN_TENANT, MAIN_TENANT);
    }

    @Override
    protected String selectExecutionsQuery() {
        return "SELECT `key`, `value` FROM `executions` WHERE JSON_LENGTH(`value`, '$.taskRunList') > 0 LIMIT ? OFFSET ?";
    }

    @Override
    protected String updateExecutionQuery() {
        return "UPDATE `executions` SET `value` = ? WHERE `key` = ?";
    }
}
