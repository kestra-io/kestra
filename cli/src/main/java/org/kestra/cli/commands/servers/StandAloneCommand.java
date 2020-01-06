package org.kestra.cli.commands.servers;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.repositories.LocalFlowRepositoryLoader;
import org.kestra.core.runners.StandAloneRunner;
import org.kestra.core.utils.Await;
import picocli.CommandLine;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;

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

    @CommandLine.Option(names = {"-f", "--flow-path"}, description = "the flow path containing flow to inject at startup (when runinng with an memory flow repository)")
    private File flowPath;

    public StandAloneCommand() {
        super(true);
    }

    @Override
    public void run() {
        super.run();

        if (flowPath != null) {
            try {
                LocalFlowRepositoryLoader localFlowRepositoryLoader = applicationContext.getBean(LocalFlowRepositoryLoader.class);
                localFlowRepositoryLoader.load(this.flowPath);
            } catch (IOException e) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), "Invalid flow path", e);
            }
        }

        StandAloneRunner standAloneRunner = applicationContext.getBean(StandAloneRunner.class);
        standAloneRunner.run();

        Await.until(() -> !standAloneRunner.isRunning());
    }
}