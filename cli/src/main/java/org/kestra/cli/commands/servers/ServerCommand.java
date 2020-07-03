package org.kestra.cli.commands.servers;

import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.cli.App;
import picocli.CommandLine;

@CommandLine.Command(
    name = "server",
    description = "handle servers",
    mixinStandardHelpOptions = true,
    subcommands = {
        ExecutorCommand.class,
        IndexerCommand.class,
        SchedulerCommand.class,
        StandAloneCommand.class,
        WebServerCommand.class,
        WorkerCommand.class,
    }
)
@Slf4j
public class ServerCommand extends AbstractCommand {
    public ServerCommand() {
        super(false);
    }

    @SneakyThrows
    @Override
    public void run() {
        super.run();

        PicocliRunner.call(App.class, "server",  "--help");
    }
}
