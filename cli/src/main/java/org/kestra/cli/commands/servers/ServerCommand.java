package org.kestra.cli.commands.servers;

import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
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
}
