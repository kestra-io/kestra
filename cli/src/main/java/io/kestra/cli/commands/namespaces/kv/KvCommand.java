package io.kestra.cli.commands.namespaces.kv;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "kv",
    description = "Manage KV Store",
    mixinStandardHelpOptions = true,
    subcommands = {
        KvUpdateCommand.class,
    }
)
@Slf4j
public class KvCommand extends AbstractCommand {
    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        PicocliRunner.call(App.class, "namespace", "kv",  "--help");

        return 0;
    }
}
