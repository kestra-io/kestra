package io.kestra.cli.commands.servers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import com.google.common.collect.ImmutableMap;

import io.kestra.cli.services.TenantIdSelectorService;
import io.kestra.core.models.ServerType;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.Executor;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.worker.systemworker.SystemWorker;
import org.awaitility.Awaitility;

import io.micronaut.context.BeanProvider;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import io.kestra.core.utils.Await;

@CommandLine.Command(
    name = "executor",
    description = "Start the Kestra executor"
)
@Slf4j
public class ExecutorCommand extends AbstractServerCommand {
    private ExecutorService poolExecutor;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    private ExecutorsUtils executorsUtils;

    @Inject
    private Provider<IgnoreExecutionService> ignoreExecutionService;

    @Inject
    private Provider<LocalFlowRepositoryLoader> localFlowRepositoryLoader;

    @Inject
    private Provider<TenantIdSelectorService> tenantIdSelectorService;

    @Inject
    private Provider<Executor> executorService;

    @Inject
    private BeanProvider<SystemWorker> systemWorker;

    @CommandLine.Option(names = { "-f", "--flow-path" }, description = "Tenant identifier required to load flows from the specified path")
    private File flowPath;

    @CommandLine.Option(names = "--tenant", description = "Tenant identifier, Required to load flows from path")
    private String tenantId;

    @CommandLine.Option(names = { "--ignore-executions" }, split = ",", description = "a list of execution identifiers to ignore, separated by a coma; for troubleshooting only")
    private List<String> ignoreExecutions = Collections.emptyList();

    @CommandLine.Option(names = { "--ignore-flows" }, split = ",", description = "a list of flow identifiers (namespace.flowId) to ignore, separated by a coma; for troubleshooting only")
    private List<String> ignoreFlows = Collections.emptyList();

    @CommandLine.Option(
        names = { "--ignore-namespaces" }, split = ",", description = "a list of namespace identifiers (tenant|namespace) to skip, separated by a coma; for troubleshooting only"
    )
    private List<String> ignoreNamespaces = Collections.emptyList();

    @CommandLine.Option(names = { "--ignore-tenants" }, split = ",", description = "a list of tenants to ignore, separated by a coma; for troubleshooting only")
    private List<String> ignoreTenants = Collections.emptyList();

    @CommandLine.Option(names = { "--ignore-queue-records" }, split = ",", description = "a list of queue record keys to ignore, separated by a coma; for troubleshooting only")
    private List<String> ignoreQueueRecords = Collections.emptyList();

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return ImmutableMap.of(
            "kestra.server-type", ServerType.EXECUTOR
        );
    }

    @Override
    public Integer call() throws Exception {
        this.ignoreExecutionService.get().setIgnoredExecutions(ignoreExecutions);
        this.ignoreExecutionService.get().setIgnoredFlows(ignoreFlows);
        this.ignoreExecutionService.get().setIgnoredNamespaces(ignoreNamespaces);
        this.ignoreExecutionService.get().setIgnoredTenants(ignoreTenants);

        super.call();

        if (flowPath != null) {
            try {
                localFlowRepositoryLoader.get().load(tenantIdSelectorService.get().getTenantId(this.tenantId), this.flowPath);
            } catch (IOException e) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), "Invalid flow path", e);
            }
        }

        executorService.get().run();

        // start the embedded SystemWorker (always present in EXECUTOR mode)
        poolExecutor = executorsUtils.cachedThreadPool("embedded-services");
        log.info("Starting the embedded SystemWorker.");
        poolExecutor.execute(systemWorker.get()::start);

        shutdownHook(true, () -> poolExecutor.shutdown());

        Await.await().forever().until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
