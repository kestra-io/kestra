package org.kestra.cli.commands.sys;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.templates.Template;
import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.repositories.TemplateRepositoryInterface;
import org.kestra.core.repositories.TriggerRepositoryInterface;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;

@CommandLine.Command(
    name = "restore-queue",
    description = {"send all data from a repository to kafka.",
        "Mostly usefull to send all flows, templates & triggers from repository to kafka in case of restore."
    }
)
@Slf4j
public class RestoreQueueCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    public RestoreQueueCommand() {
        super(false);
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        // flows
        FlowRepositoryInterface flowRepository = applicationContext.getBean(FlowRepositoryInterface.class);
        List<Flow> flows = flowRepository
            .findAll()
            .stream()
            .flatMap(flow -> flowRepository.findRevisions(flow.getNamespace(), flow.getId()).stream())
            .collect(Collectors.toList());
        this.send(flows, QueueFactoryInterface.FLOW_NAMED);

        // templates
        TemplateRepositoryInterface templateRepository = applicationContext.getBean(TemplateRepositoryInterface.class);
        List<Template> templates = new ArrayList<>(templateRepository.findAll());
        this.send(templates, QueueFactoryInterface.TEMPLATE_NAMED);

        // trigger
        TriggerRepositoryInterface triggerRepository = applicationContext.getBean(TriggerRepositoryInterface.class);
        List<Trigger> triggers = new ArrayList<>(triggerRepository.findAll());
        this.send(triggers, QueueFactoryInterface.TRIGGER_NAMED);

        return 0;
    }

    @SuppressWarnings("unchecked")
    private <T> void send(List<T> list, String queueName) {
        QueueInterface<T> flowQueue = (QueueInterface<T>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(queueName)
        );


        list.forEach(flowQueue::emit);

        stdOut("Successfully send {1} flows to {2}", list.size(), queueName);
    }
}
