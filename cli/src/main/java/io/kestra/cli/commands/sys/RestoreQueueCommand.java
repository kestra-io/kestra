package io.kestra.cli.commands.sys;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import io.kestra.cli.AbstractCommand;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.runner.kafka.services.KafkaAdminService;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    @CommandLine.Option(names = {"--no-recreate"}, description = "Don't drop the topic and recreate it")
    private boolean noRecreate = false;

    @CommandLine.Option(names = {"--no-flows"}, description = "Don't send flow")
    private boolean noFlows = false;

    @CommandLine.Option(names = {"--no-templates"}, description = "Don't send flow")
    private boolean noTemplates = false;

    @CommandLine.Option(names = {"--no-triggers"}, description = "Don't send flow")
    private boolean noTriggers = false;

    public RestoreQueueCommand() {
        super(false);
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        // flows
        if (!noFlows) {
            FlowRepositoryInterface flowRepository = applicationContext.getBean(FlowRepositoryInterface.class);
            List<Flow> flows = flowRepository
                .findAll()
                .stream()
                .flatMap(flow -> flowRepository.findRevisions(flow.getNamespace(), flow.getId()).stream())
                .collect(Collectors.toList());
            this.send(flows, QueueFactoryInterface.FLOW_NAMED, Flow.class);
        }

        // templates
        if (!this.noTemplates) {
            TemplateRepositoryInterface templateRepository = applicationContext.getBean(TemplateRepositoryInterface.class);
            List<Template> templates = new ArrayList<>(templateRepository.findAll());
            this.send(templates, QueueFactoryInterface.TEMPLATE_NAMED, Template.class);
        }

        // trigger
        if (!this.noTriggers) {
            TriggerRepositoryInterface triggerRepository = applicationContext.getBean(TriggerRepositoryInterface.class);
            List<Trigger> triggers = new ArrayList<>(triggerRepository.findAll());
            this.send(triggers, QueueFactoryInterface.TRIGGER_NAMED, Trigger.class);
        }

        return 0;
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    private <T> void send(List<T> list, String queueName, Class<?> cls) {
        Optional<String> queueType = applicationContext.getProperty("kestra.queue.type", String.class);

        if (queueType.isPresent() && queueType.get().equals("kafka")) {
            KafkaAdminService kafkaAdminService = applicationContext.getBean(KafkaAdminService.class);
            if (!noRecreate) {
                kafkaAdminService.delete(cls);
            }

            // need some wait to be sure the topic are deleted before recreated with right configuration
            Thread.sleep(2000);
            kafkaAdminService.createIfNotExist(cls);
        }

        QueueInterface<T> queue = (QueueInterface<T>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(queueName)
        );

        list.forEach(queue::emit);

        stdOut("Successfully send {0} flows to {1}", list.size(), queueName);
    }
}
