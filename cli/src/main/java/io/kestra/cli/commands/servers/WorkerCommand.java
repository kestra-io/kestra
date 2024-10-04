package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.runners.Worker;
import io.kestra.core.utils.Await;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.util.Map;
import java.util.UUID;

@CommandLine.Command(
    name = "worker",
    description = "start a worker"
)
@Slf4j
public class WorkerCommand extends AbstractServerCommand {

    @Inject
    private ApplicationContext applicationContext;

    @Option(names = {"-t", "--thread", "--threads"}, description = "the max number of worker threads, defaults to two times the number of available processors")
    private int threads = defaultWorkerThreads();

    @Option(names = {"-r", "--realtime-trigger-threads"}, description = "the max number of realtime trigger worker threads, defaults to -1 meaning unlimited")
    private int realtimeTriggerThreads = -1;

    @Option(names = {"-g", "--worker-group"}, description = "the worker group key, must match the regex [a-zA-Z0-9_-]+ (EE only)")
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
        this.shutdownHook(() -> KestraContext.getContext().shutdown());
        if (this.workerGroupKey != null && !this.workerGroupKey.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("The --worker-group option must match the [a-zA-Z0-9_-]+ pattern");
        }

        // FIXME: For backward-compatibility with Kestra 0.15.x and earliest we still use UUID for Worker ID instead of IdUtils
        String workerID = UUID.randomUUID().toString();
        Worker worker = applicationContext.createBean(Worker.class, workerID, this.threads, this.realtimeTriggerThreads, this.workerGroupKey);
        applicationContext.registerSingleton(worker);

        worker.run();

        if (this.workerGroupKey != null) {
            log.info("Worker started with {} thread(s) and {} realtime trigger thread(s) in group '{}'", this.threads, displayRealtimeTriggerThreads(realtimeTriggerThreads), this.workerGroupKey);
        }
        else {
            log.info("Worker started with {} thread(s) and {} realtime trigger thread(s)", displayRealtimeTriggerThreads(realtimeTriggerThreads), this.threads);
        }

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }

    public String workerGroupKey() {
        return workerGroupKey;
    }
}
