package io.kestra.cli.commands.sys;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.TaskDefaultService;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(
    name = "reindex",
    description = "reindex all records of a type: read them from the database then update them",
    mixinStandardHelpOptions = true,
    subcommands = {
        RestoreQueueCommand.class,
        FlowListenersRestoreCommand.class
    }
)
@Slf4j
public class ReindexCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Option(names = {"-t", "--type"}, description = "The type of the records to reindex, only 'flow' is supported for now.")
    private String type;

    @Override
    public Integer call() throws Exception {
        super.call();

        if ("flow".equals(type)) {
            FlowRepositoryInterface flowRepository = applicationContext.getBean(FlowRepositoryInterface.class);

            List<FlowWithSource> flows = flowRepository.findWithSource(null, null, null);
            flows.forEach(flow -> flowRepository.update(flow.toFlow(), flow.toFlow(), flow.getSource(), flow.toFlow()));

            stdOut("Successfully reindex " + flows.size() + " flow(s).");
        }
        else {
            throw new IllegalArgumentException("Reindexing type '" + type + "' is not supported");
        }

        return 0;
    }
}
