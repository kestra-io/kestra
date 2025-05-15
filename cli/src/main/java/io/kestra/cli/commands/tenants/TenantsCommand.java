package io.kestra.cli.commands.tenants;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "tenants-oss",
    description = "handle tenants",
    mixinStandardHelpOptions = true,
    subcommands = {
        MigrationCommand.class
    }
)
@Slf4j
public class TenantsCommand extends AbstractCommand {
    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        PicocliRunner.call(App.class, "tenants-oss",  "--help");

        return 0;
    }
}
