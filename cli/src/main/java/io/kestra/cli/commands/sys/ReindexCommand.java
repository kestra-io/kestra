package io.kestra.cli.commands.sys;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowService;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.util.List;
import java.util.Objects;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@CommandLine.Command(
    name = "reindex",
    description = "Reindex all records of a type: read them from the database then update them",
    mixinStandardHelpOptions = true
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
            FlowService flowService = applicationContext.getBean(FlowService.class);

            List<Flow> allFlow = flowRepository.findAllForAllTenants();
            allFlow.stream()
                .map(flow -> flowRepository.findByIdWithSource(flow.getTenantId(), flow.getNamespace(), flow.getId()).orElse(null))
                .filter(Objects::nonNull)
                .forEach(throwConsumer(flow -> flowService.update(GenericFlow.of(flow), flow)));

            stdOut("Successfully reindex " + allFlow.size() + " flow(s).");
        }
        else {
            throw new IllegalArgumentException("Reindexing type '" + type + "' is not supported");
        }

        return 0;
    }
}
