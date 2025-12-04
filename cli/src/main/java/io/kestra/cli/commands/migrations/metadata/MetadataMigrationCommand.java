package io.kestra.cli.commands.migrations.metadata;

import io.kestra.cli.AbstractCommand;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "metadata",
    description = "populate metadata for entities",
    subcommands = {
        KvMetadataMigrationCommand.class,
        SecretsMetadataMigrationCommand.class,
        NsFilesMetadataMigrationCommand.class
    }
)
@Slf4j
public class MetadataMigrationCommand extends AbstractCommand {
    @Override
    public Integer call() throws Exception {
        super.call();
        return 0;
    }
}
