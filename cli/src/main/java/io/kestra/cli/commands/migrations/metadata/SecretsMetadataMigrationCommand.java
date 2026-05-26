package io.kestra.cli.commands.migrations.metadata;

import io.kestra.cli.AbstractCommand;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "secrets",
    description = "populate metadata for secrets"
)
@Slf4j
public class SecretsMetadataMigrationCommand extends AbstractCommand {
    @Inject
    private Provider<MetadataMigrationService> metadataMigrationServiceProvider;

    @CommandLine.Option(names = { "-t", "--tenant" }, description = "Restrict the migration to a single tenant ID. If omitted, all tenants are migrated.")
    public String tenant;

    @Override
    public Integer call() throws Exception {
        super.call();
        try {
            metadataMigrationServiceProvider.get().secretMigration(tenant);
        } catch (Exception e) {
            System.err.println("❌ Secrets Metadata migration failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
        System.out.println("✅ Secrets Metadata migration complete.");
        return 0;
    }
}
