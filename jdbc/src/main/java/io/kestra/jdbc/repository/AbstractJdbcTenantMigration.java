package io.kestra.jdbc.repository;

import io.kestra.core.repositories.TenantMigrationInterface;
import io.kestra.jdbc.JooqDSLContextWrapper;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

@Slf4j
public abstract class AbstractJdbcTenantMigration implements TenantMigrationInterface {

    private static final List<String> KEY_TABLES = List.of("dashboards", "flows", "multipleconditions",
        "namespaces", "testsuites", "triggers", "templates");

    protected final JooqDSLContextWrapper dslContextWrapper;

    protected AbstractJdbcTenantMigration(JooqDSLContextWrapper dslContextWrapper) {
        this.dslContextWrapper = dslContextWrapper;
    }

    public void migrateTenant(String tenantId, boolean dryRun) {
        migrate(dryRun);
    }

    public void migrate(boolean dryRun) {
        List<Table<?>> tables = dslContextWrapper.transactionResult(configuration -> {
            DSLContext context = DSL.using(configuration);
            return context.meta().getTables();
        });

        log.info("📦 Found {} tables.\n", tables.size());

        int totalAffected = 0;

        for (Table<?> table : tables) {
            Field<String> tenantField = table.field("tenant_id", String.class);

            if (tenantField == null) {
                continue;
            }

            if (!dryRun) {
                if ("flows".equalsIgnoreCase(table.getName()) || "triggers".equalsIgnoreCase(table.getName())){
                    log.info("🔸 Delete tutorial flows to prevent duplication");
                    int deleted = dslContextWrapper.transactionResult(configuration -> {
                        DSLContext context = DSL.using(configuration);
                        return deleteTutorialFlows(table, context);
                    });
                    log.info("✅ {} tutorial flows have been deleted", deleted);
                }

                int updated;
                if (tableWithKey(table.getName())){
                    updated = dslContextWrapper.transactionResult(configuration -> {
                        DSLContext context = DSL.using(configuration);
                        return updateTenantIdFieldAndKey(table, context);
                    });
                } else {
                    updated = dslContextWrapper.transactionResult(configuration -> {
                        DSLContext context = DSL.using(configuration);
                        return updateTenantIdField(table, context);
                    });
                }
                totalAffected += updated;
                log.info("✅ Updated {} row(s) in {}", updated, table.getName());
            } else {
                Condition condition = tenantField.isNull();
                int count = dslContextWrapper.transactionResult(configuration -> {
                    DSLContext context = DSL.using(configuration);
                    return context.selectCount()
                        .from(table)
                        .where(condition)
                        .fetchOne(0, int.class);
                });
                if (count > 0) {
                    log.info("🔸 {}: {} row(s) to update.", table.getName(), count);
                    totalAffected += count;
                } else {
                    log.info("✅ {}: No updates needed.", table.getName());
                }
            }
        }

        if (dryRun) {
            log.info("🧪 Dry-run complete. {} row(s) would be updated.", totalAffected);
        } else {
            log.info("✅ Update complete. {} row(s) updated.", totalAffected);
        }
    }


    private static boolean tableWithKey(String tableName){
        return KEY_TABLES.stream().anyMatch(name -> tableName.toLowerCase(Locale.ROOT).contains(name));
    }

    protected abstract int updateTenantIdField(Table<?> table, DSLContext context);

    protected abstract int updateTenantIdFieldAndKey(Table<?> table, DSLContext context);

    protected int deleteTutorialFlows(Table<?> table, DSLContext context){
        String query = "DELETE FROM %s WHERE namespace = ?".formatted(table.getName());
        return context.execute(query, "tutorial");
    }

}
