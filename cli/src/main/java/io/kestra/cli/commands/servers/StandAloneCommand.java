package io.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.StandAloneRunner;
import io.kestra.core.services.SkipExecutionService;
import io.kestra.core.utils.Await;
import io.micronaut.context.ApplicationContext;
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
    description = "start a standalone server"
)
@Slf4j
public class StandAloneCommand extends AbstractServerCommand {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private SkipExecutionService skipExecutionService;

    @CommandLine.Option(names = {"-f", "--flow-path"}, description = "the flow path containing flow to inject at startup (when running with a memory flow repository)")
    private File flowPath;

    @CommandLine.Option(names = {"--worker-thread"}, description = "the number of worker thread")
    private Integer workerThread;

    @CommandLine.Option(names = {"--skip-executions"}, split=",", description = "a list of execution identifiers to skip, separated by a coma; for troubleshooting purpose only")
    private List<String> skipExecutions = Collections.emptyList();

    @CommandLine.Option(names = {"--skip-flows"}, split=",", description = "a list of flow identifiers (namespace.flowId) to skip, separated by a coma; for troubleshooting purpose only")
    private List<String> skipFlows = Collections.emptyList();

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

        super.call();
        this.shutdownHook(() -> KestraContext.getContext().shutdown());

        if (flowPath != null) {
            try {
                LocalFlowRepositoryLoader localFlowRepositoryLoader = applicationContext.getBean(LocalFlowRepositoryLoader.class);
                localFlowRepositoryLoader.load(this.flowPath, true);
            } catch (IOException e) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), "Invalid flow path", e);
            }
        }

        StandAloneRunner standAloneRunner = applicationContext.getBean(StandAloneRunner.class);

        if (this.workerThread != null  && this.workerThread == 0) {
            standAloneRunner.setWorkerEnabled(false);
        } else if (this.workerThread != null) {
            standAloneRunner.setWorkerThread(this.workerThread);
        }

        standAloneRunner.run();

        Await.until(() -> !this.applicationContext.isRunning());

        return 0;
    }
}
