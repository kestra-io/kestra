package io.kestra.cli.commands.migrations.metadata;

import io.kestra.cli.AbstractCommand;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "nsfiles",
    description = "populate metadata for Namespace Files"
)
@Slf4j
public class NsFilesMetadataMigrationCommand extends AbstractCommand {
    @Inject
    private Provider<MetadataMigrationService> metadataMigrationServiceProvider;

    @Override
    public Integer call() throws Exception {
        super.call();
        try {
            metadataMigrationServiceProvider.get().nsFilesMigration();
        } catch (Exception e) {
            System.err.println("❌ Namespace Files Metadata migration failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
        System.out.println("✅ Namespace Files Metadata migration complete.");
        return 0;
    }
}
