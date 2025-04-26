package io.kestra.cli.commands.servers;

import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import picocli.CommandLine;

@CommandLine.Command(
    name = "server",
    description = "Manage servers",
    mixinStandardHelpOptions = true,
    subcommands = {
        ExecutorCommand.class,
        IndexerCommand.class,
        SchedulerCommand.class,
        StandAloneCommand.class,
        WebServerCommand.class,
        WorkerCommand.class,
        LocalCommand.class,
    }
)
@Slf4j
public class ServerCommand extends AbstractCommand {
    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        PicocliRunner.call(App.class, "server",  "--help");

        return 0;
    }
}
