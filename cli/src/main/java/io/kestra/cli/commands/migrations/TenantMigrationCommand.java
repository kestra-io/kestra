package io.kestra.cli.commands.migrations;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.repositories.TenantMigrationInterface;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Option;

@CommandLine.Command(
    name = "default-tenant",
    description = "migrate every elements from no tenant to the main tenant"
)
@Slf4j
public class TenantMigrationCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    @Option(names = "--tenant-id", description = "tenant identifier")
    String tenantId;

    @Option(names = "--tenant-name", description = "tenant name")
    String tenantName;

    @Option(names = "--dry-run", description = "Preview only, do not update")
    boolean dryRun;

    @Override
    public Integer call() throws Exception {
        super.call();

        if (dryRun) {
            System.out.println("🧪 Dry-run mode enabled. No changes will be applied.");
        }

        TenantMigrationService migrationService = this.applicationContext.getBean(TenantMigrationService.class);
        try {
            migrationService.migrateTenant(tenantId, tenantName, dryRun);
            System.out.println("✅ Tenant migration complete.");
        } catch (Exception e) {
            System.err.println("❌ Tenant migration failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
        return 0;
    }

}
