package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.cli.services.FileChangedEventListener;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.StandAloneRunner;
import io.kestra.core.services.SkipExecutionService;
import io.kestra.core.services.StartExecutorService;
import io.kestra.core.utils.Await;
import io.micronaut.context.ApplicationContext;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@CommandLine.Command(
    name = "standalone",
    description = "Start the standalone all-in-one server"
)
@Slf4j
public class StandAloneCommand extends AbstractServerCommand {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private SkipExecutionService skipExecutionService;

    @Inject
    private StartExecutorService startExecutorService;

    @Inject
    @Nullable
    private FileChangedEventListener fileWatcher;

    @CommandLine.Option(names = {"-f", "--flow-path"}, description = "the flow path containing flow to inject at startup (when running with a memory flow repository)")
    private File flowPath;

    @CommandLine.Option(names = {"--worker-thread"}, description = "the number of worker threads, defaults to four times the number of available processors. Set it to 0 to avoid starting a worker.")
    private int workerThread = defaultWorkerThread();

    @CommandLine.Option(names = {"--skip-executions"}, split=",", description = "a list of execution identifiers to skip, separated by a coma; for troubleshooting purpose only")
    private List<String> skipExecutions = Collections.emptyList();

    @CommandLine.Option(names = {"--skip-flows"}, split=",", description = "a list of flow identifiers (namespace.flowId) to skip, separated by a coma; for troubleshooting purpose only")
    private List<String> skipFlows = Collections.emptyList();

    @CommandLine.Option(names = {"--skip-namespaces"}, split=",", description = "a list of namespace identifiers (tenant|namespace) to skip, separated by a coma; for troubleshooting purpose only")
    private List<String> skipNamespaces = Collections.emptyList();

    @CommandLine.Option(names = {"--skip-tenants"}, split=",", description = "a list of tenants to skip, separated by a coma; for troubleshooting purpose only")
    private List<String> skipTenants = Collections.emptyList();

    @CommandLine.Option(names = {"--no-tutorials"}, description = "Flag to disable auto-loading of tutorial flows.")
    boolean tutorialsDisabled = false;

    @CommandLine.Option(names = {"--start-executors"}, split=",", description = "a list of Kafka Stream executors to start, separated by a command. Use it only with the Kafka queue, for debugging purpose.")
    private List<String> startExecutors = Collections.emptyList();

    @CommandLine.Option(names = {"--not-start-executors"}, split=",", description = "a list of Kafka Stream executors to not start, separated by a command. Use it only with the Kafka queue, for debugging purpose.")
    private List<String> notStartExecutors = Collections.emptyList();

    @CommandLine.Option(names = {"--no-indexer"}, description = "Flag to disable starting an embedded indexer.")
    boolean indexerDisabled = false;

    @Override
    public boolean isFlowAutoLoadEnabled() {
        return !tutorialsDisabled;
    }

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return ImmutableMap.of(
            "kestra.server-type", ServerType.STANDALONE
        );
    }

    @Override
    public Integer call() throws Exception {
        this.skipExecutionService.setSkipExecutions(skipExecutions);
        this.skipExecutionService.setSkipFlows(skipFlows);
        this.skipExecutionService.setSkipNamespaces(skipNamespaces);
        this.skipExecutionService.setSkipTenants(skipTenants);

        this.startExecutorService.applyOptions(startExecutors, notStartExecutors);

        super.call();
        this.shutdownHook(() -> KestraContext.getContext().shutdown());

        if (flowPath != null) {
            try {
                LocalFlowRepositoryLoader localFlowRepositoryLoader = applicationContext.getBean(LocalFlowRepositoryLoader.class);
                localFlowRepositoryLoader.load(this.flowPath);
            } catch (IOException e) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), "Invalid flow path", e);
            }
        }

        StandAloneRunner standAloneRunner = applicationContext.getBean(StandAloneRunner.class);

        if (this.workerThread == 0) {
            standAloneRunner.setWorkerEnabled(false);
        } else {
            standAloneRunner.setWorkerThread(this.workerThread);
        }

        if (this.indexerDisabled) {
            standAloneRunner.setIndexerEnabled(false);
        }

        standAloneRunner.run();

        if (fileWatcher != null) {
            fileWatcher.startListeningFromConfig();
        }

        this.shutdownHook(standAloneRunner::close);

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
