package io.kestra.jdbc.repository;

import io.kestra.core.repositories.TenantMigrationInterface;
import io.kestra.jdbc.JooqDSLContextWrapper;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

public abstract class AbstractJdbcTenantMigrationRepository implements TenantMigrationInterface {

    protected final JooqDSLContextWrapper dslContextWrapper;

    protected AbstractJdbcTenantMigrationRepository(JooqDSLContextWrapper dslContextWrapper) {
        this.dslContextWrapper = dslContextWrapper;
    }

    public void migrateTenant(boolean dryRun) {
        migrate(dryRun);
    }

    public void migrate(boolean dryRun) {
        List<Table<?>> tables = dslContextWrapper.transactionResult(configuration -> {
            DSLContext context = DSL.using(configuration);
            return context.meta().getTables();
        });

        System.out.printf("📦 Found %d tables.\n\n", tables.size());

        int totalAffected = 0;

        for (Table<?> table : tables) {
            Field<String> tenantField = table.field("tenant_id", String.class);

            if (tenantField == null) {
                continue;
            }

            Condition condition = tenantField.isNull();

            int count = dslContextWrapper.transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                return context.selectCount()
                    .from(table)
                    .where(condition)
                    .fetchOne(0, int.class);
            });

            if (count > 0) {
                System.out.printf("🔸 %s: %d row(s) to update.\n", table.getName(), count);
                totalAffected += count;

                if (!dryRun) {
                    int updated = dslContextWrapper.transactionResult(configuration -> {
                        DSLContext context = DSL.using(configuration);
                        return updateTenantId(table, context);
                    });
                    System.out.printf("✅ Updated %d row(s) in %s\n", updated, table.getName());
                }
            } else {
                System.out.printf("✅ %s: No updates needed.\n", table.getName());
            }
        }

        if (dryRun) {
            System.out.printf("\n🧪 Dry-run complete. %d row(s) would be updated.\n",
                totalAffected);
        } else {
            System.out.printf("\n✅ Update complete. %d row(s) updated.\n", totalAffected);
        }
    }

    protected abstract int updateTenantId(Table<?> table, DSLContext context);

}
