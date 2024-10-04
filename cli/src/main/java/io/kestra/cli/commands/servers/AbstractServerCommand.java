package io.kestra.cli.commands.servers;

import io.kestra.cli.AbstractCommand;
import picocli.CommandLine;

abstract public class AbstractServerCommand extends AbstractCommand implements ServerCommandInterface {
    @CommandLine.Option(names = {"--port"}, description = "the port to bind")
    Integer serverPort;

    protected static int defaultWorkerThreads() {
        return Runtime.getRuntime().availableProcessors() * 4;
    }

    protected String displayRealtimeTriggerThreads(int threads) {
        return threads > 0 ? String.valueOf(threads) : "unlimited";
    }
}
