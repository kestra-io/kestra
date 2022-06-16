package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.ServerType;
import io.kestra.core.runners.Worker;
import io.kestra.core.utils.Await;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.Map;

@CommandLine.Command(
    name = "worker",
    description = "start a worker"
)
@Slf4j
public class WorkerCommand extends AbstractServerCommand {
    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Option(names = {"-t", "--thread"}, description = "the max number of concurrent threads to launch")
    private int thread = Runtime.getRuntime().availableProcessors() * 2;

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
