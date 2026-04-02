package io.kestra.cli.commands.servers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

import io.kestra.cli.StandAloneRunner;
import io.kestra.cli.services.FileChangedEventListener;
import io.kestra.cli.services.TenantIdSelectorService;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.Worker;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.services.StartExecutorService;
import io.kestra.core.utils.Await;

import io.micronaut.context.ApplicationContext;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import picocli.CommandLine;
import picocli.CommandLine.Option;

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

    @Option(names = { "-f", "--flow-path" }, description = "Tenant identifier required to load flows from the specified path")
    private File flowPath;

    @Option(names = "--tenant", description = "Tenant identifier, Required to load flows from path with the enterprise edition")
    private String tenantId;

    @Option(names = { "--worker-thread" }, description = "the number of worker threads, defaults to eight times the number of available processors. Set it to 0 to avoid starting a worker.")
    private int workerThread = Worker.defaultNumThreads();
    @Option(names = { "--ignore-executions" }, split = ",", description = "a list of execution identifiers to ignore, separated by a coma; for troubleshooting only")
    private List<String> ignoreExecutions = Collections.emptyList();

    @Option(names = { "--ignore-flows" }, split = ",", description = "a list of flow identifiers (namespace.flowId) to ignore, separated by a coma; for troubleshooting only")
    private List<String> ignoreFlows = Collections.emptyList();

    @Option(names = { "--ignore-namespaces" }, split = ",", description = "a list of namespace identifiers (tenant|namespace) to skip, separated by a coma; for troubleshooting only")
    private List<String> ignoreNamespaces = Collections.emptyList();

    @Option(names = { "--ignore-tenants" }, split = ",", description = "a list of tenants to ignore, separated by a coma; for troubleshooting only")
    private List<String> ignoreTenants = Collections.emptyList();

    @Option(names = { "--ignore-indexer-records" }, split = ",", description = "a list of indexer record keys to ignore, separated by a coma; for troubleshooting only")
    private List<String> ignoreIndexerRecords = Collections.emptyList();

    @Option(names = { "--no-tutorials" }, description = "Flag to disable auto-loading of tutorial flows.")
    boolean tutorialsDisabled = false;

    @Option(
        names = { "--start-executors" }, split = ",",
        description = "a list of Kafka Stream executors to start, separated by a command. Use it only with the Kafka queue, for debugging purpose."
    )
    private List<String> startExecutors = Collections.emptyList();

    @Option(
        names = { "--not-start-executors" }, split = ",",
        description = "a list of Kafka Stream executors to not start, separated by a command. Use it only with the Kafka queue, for debugging purpose."
    )
    private List<String> notStartExecutors = Collections.emptyList();

    @Option(names = { "--no-indexer" }, description = "Flag to disable starting an embedded indexer.")
    boolean indexerDisabled = false;

    @Option(names = { "--no-controller" }, description = "Flag to disable starting an embedded controller.")
    boolean controllerDisabled = false;

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
        this.ignoreExecutionService.setIgnoredExecutions(ignoreExecutions);
        this.ignoreExecutionService.setIgnoredFlows(ignoreFlows);
        this.ignoreExecutionService.setIgnoredNamespaces(ignoreNamespaces);
        this.ignoreExecutionService.setIgnoredTenants(ignoreTenants);
        this.ignoreExecutionService.setIgnoredIndexerRecords(ignoreIndexerRecords);
        this.startExecutorService.applyOptions(startExecutors, notStartExecutors);

        KestraContext.getContext().injectWorkerConfigs(workerThread, null);

        if (tenantId != null) {
            TenantIdSelectorService tenantIdSelectorService = applicationContext.getBean(TenantIdSelectorService.class);
            tenantIdSelectorService.createTenant(tenantId);
        }

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

            if (this.controllerDisabled) {
                standAloneRunner.setControllerEnabled(false);
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
