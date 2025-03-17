package io.kestra.cli.commands.servers;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.contexts.KestraContext;
import jakarta.annotation.PostConstruct;
import picocli.CommandLine;

abstract public class AbstractServerCommand extends AbstractCommand implements ServerCommandInterface {
    @CommandLine.Option(names = {"--port"}, description = "The port to bind")
    Integer serverPort;

    @Override
    public Integer call()  throws Exception {
        this.shutdownHook(true, () -> KestraContext.getContext().shutdown());
        return super.call();
    }

    protected static int defaultWorkerThread() {
        return Runtime.getRuntime().availableProcessors() * 4;
    }
}
