package io.kestra.cli.commands.servers;

import io.kestra.cli.AbstractCommand;
import picocli.CommandLine;

import java.io.Serial;

abstract public class AbstractServerCommand extends AbstractCommand implements ServerCommandInterface {
    @CommandLine.Option(names = {"--port"}, description = "the port to bind")
    Integer serverPort;

    public static class ServerCommandException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public ServerCommandException(String errorMessage) {
            super(errorMessage);
        }
    }
}
