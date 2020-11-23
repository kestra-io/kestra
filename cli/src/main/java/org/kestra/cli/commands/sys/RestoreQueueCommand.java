package org.kestra.cli.commands.sys;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.templates.Template;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.repositories.TemplateRepositoryInterface;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;

@CommandLine.Command(
    name = "restore-queue",
    description = {"send all flows & template from a repository to the persistent queue.",
        "Mostly usefull to send all flows from repository to persistant queue in case of restore."
    }
)
@Slf4j
public class RestoreQueueCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    public RestoreQueueCommand() {
        super(false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Integer call() throws Exception {
        super.call();

        FlowRepositoryInterface flowRepository = applicationContext.getBean(FlowRepositoryInterface.class);
        QueueInterface<Flow> flowQueue = (QueueInterface<Flow>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.FLOW_NAMED)
        );

        List<Flow> flows = flowRepository
            .findAll()
            .stream()
            .flatMap(flow -> flowRepository.findRevisions(flow.getNamespace(), flow.getId()).stream())
            .collect(Collectors.toList());

        flows.forEach(flowQueue::emit);

        log.info("Successfully send {} flows to queue", flows.size());

        TemplateRepositoryInterface templateRepository = applicationContext.getBean(TemplateRepositoryInterface.class);
        QueueInterface<Template> templateQueue = (QueueInterface<Template>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.TEMPLATE_NAMED)
        );

        List<Template> templates = new ArrayList<>(templateRepository.findAll());

        templates.forEach(templateQueue::emit);

        log.info("Successfully send {} templates to queue", templates.size());

        return 0;
    }
}
