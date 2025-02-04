package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.runners.ExecutorInterface;
import io.kestra.core.services.SkipExecutionService;
import io.kestra.core.services.StartExecutorService;
import io.kestra.core.utils.Await;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@CommandLine.Command(
    name = "executor",
    description = "Start the Kestra executor"
)
@Slf4j
public class ExecutorCommand extends AbstractServerCommand {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private SkipExecutionService skipExecutionService;

    @Inject
    private StartExecutorService startExecutorService;

    @CommandLine.Option(names = {"--skip-executions"}, split=",", description = "The list of execution identifiers to skip, separated by a coma; for troubleshooting purpose only")
    private List<String> skipExecutions = Collections.emptyList();

    @CommandLine.Option(names = {"--skip-flows"}, split=",", description = "The list of flow identifiers (tenant|namespace|flowId) to skip, separated by a coma; for troubleshooting purpose only")
    private List<String> skipFlows = Collections.emptyList();

    @CommandLine.Option(names = {"--skip-namespaces"}, split=",", description = "The list of namespace identifiers (tenant|namespace) to skip, separated by a coma; for troubleshooting purpose only")
    private List<String> skipNamespaces = Collections.emptyList();

    @CommandLine.Option(names = {"--skip-tenants"}, split=",", description = "The list of tenants to skip, separated by a coma; for troubleshooting purpose only")
    private List<String> skipTenants = Collections.emptyList();

    @CommandLine.Option(names = {"--start-executors"}, split=",", description = "The list of Kafka Stream executors to start, separated by a command. Use it only with the Kafka queue, for debugging purpose.")
    private List<String> startExecutors = Collections.emptyList();

    @CommandLine.Option(names = {"--not-start-executors"}, split=",", description = "The list of Kafka Stream executors to not start, separated by a command. Use it only with the Kafka queue, for debugging purpose.")
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
        this.shutdownHook(() -> KestraContext.getContext().shutdown());

        ExecutorInterface executorService = applicationContext.getBean(ExecutorInterface.class);
        executorService.run();

        log.info("Executor started");

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
