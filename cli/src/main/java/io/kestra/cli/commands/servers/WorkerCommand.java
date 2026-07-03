package io.kestra.cli.commands.servers;

import java.util.Map;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.runners.Worker;
import org.awaitility.Awaitility;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import io.kestra.core.utils.Await;

@CommandLine.Command(
    name = "worker",
    description = "Start the Kestra worker"
)
public class WorkerCommand extends AbstractServerCommand {

    @Inject
    private Provider<Worker> workerProvider;

    @Option(names = { "-t", "--thread" }, description = "The max number of worker threads, defaults to eight times the number of available processors")
    private int thread = Worker.defaultNumThreads();

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return Map.of("kestra.server-type", ServerType.WORKER);
    }

    @Override
    public Integer call() throws Exception {

        KestraContext.getContext().injectWorkerConfigs(thread);

        super.call();

        workerProvider.get().start(this.thread);

        Await.await().forever().until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
