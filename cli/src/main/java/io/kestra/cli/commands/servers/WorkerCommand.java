package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import io.kestra.cli.AbstractCommand;
import io.kestra.core.models.ServerType;
import io.kestra.core.runners.Worker;
import io.kestra.core.utils.Await;
import picocli.CommandLine;

import java.util.Map;
import javax.inject.Inject;

@CommandLine.Command(
    name = "worker",
    description = "start a worker"
)
@Slf4j
public class WorkerCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Option(names = {"-t", "--thread"}, description = "the number of concurrent threads to launch")
    private int thread = Runtime.getRuntime().availableProcessors();

    public WorkerCommand() {
        super(true);
    }

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return ImmutableMap.of(
            "kestra.server-type", ServerType.WORKER
        );
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        Worker worker = new Worker(applicationContext, this.thread);
        applicationContext.registerSingleton(worker);

        worker.run();

        log.info("Workers started with {} thread(s)", this.thread);

        this.shutdownHook(worker::close);

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
