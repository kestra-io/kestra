package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.cli.services.TenantIdSelectorService;
import io.kestra.core.models.ServerType;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.ExecutorInterface;
import io.kestra.core.services.SkipExecutionService;
import io.kestra.core.services.StartExecutorService;
import io.kestra.core.utils.Await;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    private SkipExecutionService skipExecutionService;

    @Inject
    private StartExecutorService startExecutorService;

    @CommandLine.Option(names = {"-f", "--flow-path"}, description = "Tenant identifier required to load flows from the specified path")
    private File flowPath;

    @CommandLine.Option(names = "--tenant", description = "Tenant identifier, Required to load flows from path")
    private String tenantId;

    @CommandLine.Option(names = {"--skip-executions"}, split=",", description = "List of execution IDs to skip, separated by commas; for troubleshooting only")
    private List<String> skipExecutions = Collections.emptyList();

    @CommandLine.Option(names = {"--skip-flows"}, split=",", description = "List of flow identifiers (tenant|namespace|flowId) to skip, separated by a coma; for troubleshooting only")
    private List<String> skipFlows = Collections.emptyList();

    @CommandLine.Option(names = {"--skip-namespaces"}, split=",", description = "List of namespace identifiers (tenant|namespace) to skip, separated by a coma; for troubleshooting only")
    private List<String> skipNamespaces = Collections.emptyList();

    @CommandLine.Option(names = {"--skip-tenants"}, split=",", description = "List of tenants to skip, separated by a coma; for troubleshooting only")
    private List<String> skipTenants = Collections.emptyList();

    @CommandLine.Option(names = {"--start-executors"}, split=",", description = "List of Kafka Stream executors to start, separated by a command. Use it only with the Kafka queue; for debugging only")
    private List<String> startExecutors = Collections.emptyList();

    @CommandLine.Option(names = {"--not-start-executors"}, split=",", description = "Lst of Kafka Stream executors to not start, separated by a command. Use it only with the Kafka queue; for debugging only")
    private List<String> notStartExecutors = Collections.emptyList();

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return ImmutableMap.of(
            "kestra.server-type", ServerType.EXECUTOR
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

        if (flowPath != null) {
            try {
                LocalFlowRepositoryLoader localFlowRepositoryLoader = applicationContext.getBean(LocalFlowRepositoryLoader.class);
                TenantIdSelectorService tenantIdSelectorService = applicationContext.getBean(TenantIdSelectorService.class);
                localFlowRepositoryLoader.load(tenantIdSelectorService.getTenantId(this.tenantId), this.flowPath);
            } catch (IOException e) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), "Invalid flow path", e);
            }
        }

        ExecutorInterface executorService = applicationContext.getBean(ExecutorInterface.class);
        executorService.run();

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
