package org.kestra.cli.commands.servers;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.repositories.LocalFlowRepositoryLoader;
import picocli.CommandLine;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;

@CommandLine.Command(
    mixinStandardHelpOptions = true
)
@Slf4j
abstract public class AbstractServerCommand extends AbstractCommand {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Option(names = {"-f", "--flow-path"}, description = "the flow path (when runinng with an memory flow repository)")
    private File flowPath;

    public AbstractServerCommand() {
        super(true);
    }

    @Override
    public void run() {
        if (flowPath != null) {
            try {
                LocalFlowRepositoryLoader localFlowRepositoryLoader = applicationContext.getBean(LocalFlowRepositoryLoader.class);
                localFlowRepositoryLoader.load(this.flowPath);
            } catch (IOException e) {
                throw new CommandLine.ParameterException(this.spec.commandLine(), "Invalid flow path", e);
            }
        }

        super.run();
    }
}
