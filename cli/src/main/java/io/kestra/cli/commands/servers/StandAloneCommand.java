package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.cli.services.FileChangedEventListener;
import io.kestra.cli.services.TenantIdSelectorService;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.cli.StandAloneRunner;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.services.StartExecutorService;
import io.kestra.core.utils.Await;
import io.micronaut.context.ApplicationContext;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
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
public class StandAloneCommand extends AbstractServerCommand {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private IgnoreExecutionService ignoreExecutionService;

    @Inject
    private StartExecutorService startExecutorService;

    @Inject
    @Nullable
    private FileChangedEventListener fileWatcher;

    @CommandLine.Option(names = {"-f", "--flow-path"}, description = "Tenant identifier required to load flows from the specified path")
    private File flowPath;

    @CommandLine.Option(names = "--tenant", description = "Tenant identifier, Required to load flows from path with the enterprise edition")
    private String tenantId;

    @CommandLine.Option(names = {"--worker-thread"}, description = "the number of worker threads, defaults to eight times the number of available processors. Set it to 0 to avoid starting a worker.")
    private int workerThread = defaultWorkerThread();

    @CommandLine.Option(names = {"--skip-executions"}, split=",", description = "deprecated - use '--ignore-executions' instead")
    @Deprecated
    private List<String> skipExecutions;

    @CommandLine.Option(names = {"--ignore-executions"}, split=",", description = "a list of execution identifiers to ignore, separated by a coma; for troubleshooting only")
    private List<String> ignoreExecutions = Collections.emptyList();

    @CommandLine.Option(names = {"--skip-flows"}, split=",", description = "deprecated - use '--ignore-flows' instead")
    @Deprecated
    private List<String> skipFlows;

    @CommandLine.Option(names = {"--ignore-flows"}, split=",", description = "a list of flow identifiers (namespace.flowId) to ignore, separated by a coma; for troubleshooting only")
    private List<String> ignoreFlows = Collections.emptyList();

    @CommandLine.Option(names = {"--skip-namespaces"}, split=",", description = "deprecated - use 'ignore-namespaces' instead")
    @Deprecated
    private List<String> skipNamespaces;

    @CommandLine.Option(names = {"--ignore-namespaces"}, split=",", description = "a list of namespace identifiers (tenant|namespace) to skip, separated by a coma; for troubleshooting only")
    private List<String> ignoreNamespaces = Collections.emptyList();

    @CommandLine.Option(names = {"--skip-tenants"}, split=",", description = "a list of tenants to skip, separated by a coma; for troubleshooting only")
    @Deprecated
    private List<String> skipTenants;

    @CommandLine.Option(names = {"--ignore-tenants"}, split=",", description = "a list of tenants to ignore, separated by a coma; for troubleshooting only")
    private List<String> ignoreTenants = Collections.emptyList();

    @CommandLine.Option(names = {"--skip-indexer-records"}, split=",", description = "deprecated - use '--ignore-indexer-record' instead")
    @Deprecated
    private List<String> skipIndexerRecords;

    @CommandLine.Option(names = {"--ignore-indexer-records"}, split=",", description = "a list of indexer record keys to ignore, separated by a coma; for troubleshooting only")
    private List<String> ignoreIndexerRecords = Collections.emptyList();

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
        this.ignoreExecutionService.setIgnoredExecutions(skipExecutions != null ? skipExecutions : ignoreExecutions);
        this.ignoreExecutionService.setIgnoredFlows(skipFlows != null ? skipFlows : ignoreFlows);
        this.ignoreExecutionService.setIgnoredNamespaces(skipNamespaces != null ? skipNamespaces : ignoreNamespaces);
        this.ignoreExecutionService.setIgnoredTenants(skipTenants != null ? skipTenants : ignoreTenants);
        this.ignoreExecutionService.setIgnoredIndexerRecords(skipIndexerRecords != null ? skipIndexerRecords : ignoreIndexerRecords);
        this.startExecutorService.applyOptions(startExecutors, notStartExecutors);

        KestraContext.getContext().injectWorkerConfigs(workerThread, null);

        if (flowPath != null) {
            try {
                LocalFlowRepositoryLoader localFlowRepositoryLoader = applicationContext.getBean(LocalFlowRepositoryLoader.class);
                TenantIdSelectorService tenantIdSelectorService = applicationContext.getBean(TenantIdSelectorService.class);
                localFlowRepositoryLoader.load(tenantIdSelectorService.getTenantId(this.tenantId), this.flowPath);
            } catch (IOException e) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), "Invalid flow path", e);
            }
        }

        super.call();

        try (StandAloneRunner standAloneRunner = applicationContext.getBean(StandAloneRunner.class)) {

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

            Await.until(() -> !this.applicationContext.isRunning());
        }

        return 0;
    }
}
