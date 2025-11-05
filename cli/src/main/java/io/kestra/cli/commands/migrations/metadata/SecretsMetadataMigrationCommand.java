package io.kestra.cli.commands.migrations.metadata;

import io.kestra.cli.AbstractCommand;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "secrets",
    description = "populate metadata for secrets"
)
@Slf4j
public class SecretsMetadataMigrationCommand extends AbstractCommand {
    @Inject
    private MetadataMigrationService metadataMigrationService;

    @Override
    public Integer call() throws Exception {
        super.call();
        try {
            metadataMigrationService.secretMigration();
        } catch (Exception e) {
            System.err.println("❌ Secrets Metadata migration failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
        System.out.println("✅ Secrets Metadata migration complete.");
        return 0;
    }
}
