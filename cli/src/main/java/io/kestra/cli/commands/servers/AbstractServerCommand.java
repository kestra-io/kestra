package io.kestra.cli.commands.servers;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.contexts.KestraContext;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@Slf4j
public abstract class AbstractServerCommand extends AbstractCommand implements ServerCommandInterface {
    @CommandLine.Option(names = {"--port"}, description = "The port to bind")
    Integer serverPort;

    @Override
    public Integer call()  throws Exception {
        log.info("Machine information: {} available cpu(s), {}MB max memory, Java version {}", Runtime.getRuntime().availableProcessors(), maxMemoryInMB(), Runtime.version());

        this.shutdownHook(true, () -> KestraContext.getContext().shutdown());

        return super.call();
    }

    private long maxMemoryInMB() {
        return Runtime.getRuntime().maxMemory() / 1024 / 1024;
    }

    protected static int defaultWorkerThread() {
        return Runtime.getRuntime().availableProcessors() * 8;
    }
}
