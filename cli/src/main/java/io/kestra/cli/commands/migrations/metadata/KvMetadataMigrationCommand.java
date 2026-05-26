package io.kestra.cli.commands.migrations.metadata;

import io.kestra.cli.AbstractCommand;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "kv",
    description = "populate metadata for KV"
)
@Slf4j
public class KvMetadataMigrationCommand extends AbstractCommand {
    @Inject
    private Provider<MetadataMigrationService> metadataMigrationServiceProvider;

    @CommandLine.Option(names = { "-t", "--tenant" }, description = "Restrict the migration to a single tenant ID. If omitted, all tenants are migrated.")
    public String tenant;

    @Override
    public Integer call() throws Exception {
        super.call();
        try {
            metadataMigrationServiceProvider.get().kvMigration(tenant);
        } catch (Exception e) {
            System.err.println("❌ KV Metadata migration failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
        System.out.println("✅ KV Metadata migration complete.");
        return 0;
    }
}
