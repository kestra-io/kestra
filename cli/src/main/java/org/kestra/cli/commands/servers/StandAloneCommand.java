package org.kestra.cli.commands.servers;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.models.ServerType;
import org.kestra.core.repositories.LocalFlowRepositoryLoader;
import org.kestra.core.runners.AbstractExecutor;
import org.kestra.core.runners.StandAloneRunner;
import org.kestra.core.utils.Await;
import org.kestra.runner.kafka.KafkaExecutor;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;

@CommandLine.Command(
    name = "standalone",
    description = "start a standalone server"
)
@Slf4j
public class StandAloneCommand extends AbstractCommand {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Option(names = {"-f", "--flow-path"}, description = "the flow path containing flow to inject at startup (when running with a memory flow repository)")
    private File flowPath;

    public StandAloneCommand() {
        super(true);
    }

    @SuppressWarnings("unused")
    public static Map<String, Object> propertiesOverrides() {
        return ImmutableMap.of(
            "kestra.server-type", ServerType.STANDALONE
        );
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        if (flowPath != null) {
            try {
                LocalFlowRepositoryLoader localFlowRepositoryLoader = applicationContext.getBean(LocalFlowRepositoryLoader.class);
                localFlowRepositoryLoader.load(this.flowPath);
            } catch (IOException e) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), "Invalid flow path", e);
            }
        }

        StandAloneRunner standAloneRunner = applicationContext.getBean(StandAloneRunner.class);

        Optional<AbstractExecutor> executor = applicationContext.findBean(AbstractExecutor.class);
        if (executor.isPresent() && executor.get() instanceof KafkaExecutor) {
            standAloneRunner.setExecutorThreads(1);
        }

        standAloneRunner.run();

        Await.until(() -> !standAloneRunner.isRunning());

        return 0;
    }
}
