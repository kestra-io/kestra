package io.kestra.cli.services;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.runner.kafka.services.KafkaAdminService;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RestoreQueueService {
    @Inject
    ApplicationContext applicationContext;

    public int flows(boolean noRecreate) {
        FlowRepositoryInterface flowRepository = applicationContext.getBean(FlowRepositoryInterface.class);

        List<Flow> flows = flowRepository
            .findAll()
            .stream()
            .flatMap(flow -> flowRepository.findRevisions(flow.getNamespace(), flow.getId()).stream())
            .collect(Collectors.toList());

        return this.send(flows, QueueFactoryInterface.FLOW_NAMED, Flow.class, noRecreate);
    }

    public int templates(boolean noRecreate) {
        TemplateRepositoryInterface templateRepository = applicationContext.getBean(TemplateRepositoryInterface.class);
        List<Template> templates = new ArrayList<>(templateRepository.findAll());

        return this.send(templates, QueueFactoryInterface.TEMPLATE_NAMED, Template.class, noRecreate);
    }

    public int triggers(boolean noRecreate) {
        TriggerRepositoryInterface triggerRepository = applicationContext.getBean(TriggerRepositoryInterface.class);
        List<Trigger> triggers = new ArrayList<>(triggerRepository.findAll());

        return this.send(triggers, QueueFactoryInterface.TRIGGER_NAMED, Trigger.class, noRecreate);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public <T> int send(List<T> list, String queueName, Class<?> cls, boolean noRecreate) {
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

        return list.size();
    }
}
