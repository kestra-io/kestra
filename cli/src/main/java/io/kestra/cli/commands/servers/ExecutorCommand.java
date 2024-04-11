package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.ServerType;
import io.kestra.core.runners.ExecutorInterface;
import io.kestra.core.services.SkipExecutionService;
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
    description = "start an executor"
)
@Slf4j
public class ExecutorCommand extends AbstractServerCommand {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private SkipExecutionService skipExecutionService;

    @CommandLine.Option(names = {"--skip-executions"}, split=",", description = "a list of execution identifiers to skip, separated by a coma; for troubleshooting purpose only")
    private List<String> skipExecutions = Collections.emptyList();

    @CommandLine.Option(names = {"--skip-flows"}, split=",", description = "a list of flow identifiers (tenant|namespace|flowId) to skip, separated by a coma; for troubleshooting purpose only")
    private List<String> skipFlows = Collections.emptyList();

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

        super.call();

        ExecutorInterface executorService = applicationContext.getBean(ExecutorInterface.class);
        executorService.run();

        log.info("Executor started");

        this.shutdownHook(executorService::close);

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
