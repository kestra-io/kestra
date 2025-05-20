package io.kestra.jdbc.repository;

import io.kestra.core.repositories.TenantMigrationInterface;
import io.kestra.jdbc.JooqDSLContextWrapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

@Slf4j
public abstract class AbstractJdbcTenantMigration implements TenantMigrationInterface {

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
                int updated = dslContextWrapper.transactionResult(configuration -> {
                    DSLContext context = DSL.using(configuration);
                    return updateTenantId(table, context);
                });
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

    protected abstract int updateTenantId(Table<?> table, DSLContext context);

}
