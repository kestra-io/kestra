package org.kestra.cli.commands.sys;

import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import picocli.CommandLine;

import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;

@CommandLine.Command(
    name = "restore-flow-queue",
    description = {"send all flows from a repository to the persistent queue.",
        "Mostly usefull to send all flows from repository to persistant queue in case of restore."
    }
)
@Slf4j
public class RestoreFlowQueueCommand extends AbstractCommand {
    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    private QueueInterface<Flow> flowQueue;

    public RestoreFlowQueueCommand() {
        super(false);
    }

    @Override
    public void run() {
        super.run();

        List<Flow> list = flowRepository
            .findAll()
            .stream()
            .flatMap(flow -> flowRepository.findRevisions(flow.getNamespace(), flow.getId()).stream())
            .collect(Collectors.toList());

        list.forEach(flow -> flowQueue.emit(flow));

        log.info("Successfully send {} flow to queue", list.size());
    }
}
