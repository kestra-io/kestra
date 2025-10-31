package io.kestra.cli.commands.migrations;

import io.kestra.cli.AbstractCommand;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "metadata",
    description = "populate metadata for entities"
)
@Slf4j
public class MetadataMigrationCommand extends AbstractCommand {
    @Inject
    private MetadataMigrationService metadataMigrationService;

    @Override
    public Integer call() throws Exception {
        super.call();
        int returnCode = metadataMigrationService.migrateMetadata();
        if (returnCode == 0) {
            System.out.println("✅ Metadata migration complete.");
        }
        return returnCode;
    }
}
