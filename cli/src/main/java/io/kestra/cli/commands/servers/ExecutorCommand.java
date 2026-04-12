package io.kestra.cli.commands.servers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

import io.kestra.cli.services.TenantIdSelectorService;
import io.kestra.core.models.ServerType;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.Executor;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.Await;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import picocli.CommandLine;

@CommandLine.Command(
    name = "executor",
    description = "Start the Kestra executor"
)
public class ExecutorCommand extends AbstractServerCommand {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private IgnoreExecutionService ignoreExecutionService;

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
        this.ignoreExecutionService.setIgnoredExecutions(ignoreExecutions);
        this.ignoreExecutionService.setIgnoredFlows(ignoreFlows);
        this.ignoreExecutionService.setIgnoredNamespaces(ignoreNamespaces);
        this.ignoreExecutionService.setIgnoredTenants(ignoreTenants);
        this.ignoreExecutionService.setIgnoredQueueRecords(ignoreQueueRecords);

        super.call();

        if (flowPath != null) {
            try {
                LocalFlowRepositoryLoader localFlowRepositoryLoader = applicationContext.getBean(LocalFlowRepositoryLoader.class);
                TenantIdSelectorService tenantIdSelectorService = applicationContext.getBean(TenantIdSelectorService.class);
                localFlowRepositoryLoader.load(tenantIdSelectorService.getTenantId(this.tenantId), this.flowPath);
            } catch (IOException e) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), "Invalid flow path", e);
            }
        }

        Executor executorService = applicationContext.getBean(Executor.class);
        executorService.run();

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
