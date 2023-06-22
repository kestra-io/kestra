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

    @CommandLine.Option(names = {"-g", "--worker-group"}, description = "the worker group key, must match the regex [a-zA-Z0-9_-]+ (EE only)")
    private String workerGroupKey = null;

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return ImmutableMap.of(
            "kestra.server-type", ServerType.WORKER
        );
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        if (this.workerGroupKey != null && !this.workerGroupKey.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("The --worker-group option must match the [a-zA-Z0-9_-]+ pattern");
        }

        Worker worker = new Worker(applicationContext, this.thread, this.workerGroupKey);
        applicationContext.registerSingleton(worker);

        worker.run();

        if (this.workerGroupKey != null) {
            log.info("Worker started with {} thread(s) in group '{}'", this.thread, this.workerGroupKey);
        }
        else {
            log.info("Worker started with {} thread(s)", this.thread);
        }

        this.shutdownHook(worker::close);

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
