package io.kestra.cli.services;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class RestoreQueueService {
    @Inject
    ApplicationContext applicationContext;

    public int flows(boolean noRecreate) {
        FlowRepositoryInterface flowRepository = applicationContext.getBean(FlowRepositoryInterface.class);

        List<Flow> flows = flowRepository
            .findAllForAllTenants()
            .stream()
            .flatMap(flow -> flowRepository.findRevisions(flow.getTenantId(), flow.getNamespace(), flow.getId()).stream())
            // we can't resend FlowSource since deserialize failed & will be invalid
            .filter(flow -> !(flow instanceof FlowWithException))
            .map(FlowWithSource::toFlow)
            .collect(Collectors.toList());

        return this.send(flows, QueueFactoryInterface.FLOW_NAMED, Flow.class, noRecreate);
    }

    public int templates(boolean noRecreate) {
        TemplateRepositoryInterface templateRepository = applicationContext.getBean(TemplateRepositoryInterface.class);
        List<Template> templates = new ArrayList<>(templateRepository.findAllForAllTenants());

        return this.send(templates, QueueFactoryInterface.TEMPLATE_NAMED, Template.class, noRecreate);
    }

    public int triggers(boolean noRecreate, boolean noTriggerExecutionId) {
        TriggerRepositoryInterface triggerRepository = applicationContext.getBean(TriggerRepositoryInterface.class);
        List<Trigger> triggers = new ArrayList<>(triggerRepository.findAllForAllTenants());

        if (noTriggerExecutionId) {
            triggers = triggers
                .stream()
                .map(trigger -> trigger.toBuilder()
                    .executionId(null)
                    .build()
                )
                .collect(Collectors.toList());
        }

        return this.send(triggers, QueueFactoryInterface.TRIGGER_NAMED, Trigger.class, noRecreate);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public <T> int send(List<T> list, String queueName, Class<?> cls, boolean noRecreate) {
        QueueInterface<T> queue = (QueueInterface<T>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(queueName)
        );

        list.forEach(queue::emit);

        return list.size();
    }
}
