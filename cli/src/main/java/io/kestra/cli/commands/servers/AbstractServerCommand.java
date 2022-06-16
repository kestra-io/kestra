package io.kestra.cli.commands.servers;

import io.kestra.cli.AbstractCommand;
import picocli.CommandLine;

abstract public class AbstractServerCommand extends AbstractCommand implements ServerCommandInterface {
    @CommandLine.Option(names = {"--port"}, description = "the port to bind")
    Integer serverPort;
}
