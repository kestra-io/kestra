package org.kestra.repository.memory;

import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.value.ValueException;
import io.micronaut.data.model.Pageable;
import org.kestra.core.events.CrudEvent;
import org.kestra.core.events.CrudEventType;
import org.kestra.core.models.templates.Template;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.repositories.TemplateRepositoryInterface;

import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@MemoryRepositoryEnabled
public class MemoryTemplateRepository implements TemplateRepositoryInterface {
    private final Map<String, Template> templates = new HashMap<>();

    @Inject
    @Named(QueueFactoryInterface.TEMPLATE_NAMED)
    private QueueInterface<Template> templateQueue;

    @Inject
    private ApplicationEventPublisher eventPublisher;

    @Override
    public Optional<Template> findById(String namespace, String id) {
        return templates
            .values()
            .stream()
            .filter(template -> template.getId().equals(id)).findFirst();
    }

    @Override
    public List<Template> findAll() {
        return new ArrayList<>(templates.values());
    }

    @Override
    public ArrayListTotal<Template> find(String query, Pageable pageable) {
        if (pageable.getNumber() < 1) {
            throw new ValueException("Page cannot be < 1");
        }

        List<Template> filteredTemplates = new ArrayList<>(templates.values());

        return ArrayListTotal.of(pageable, filteredTemplates);
    }

    @Override
    public List<Template> findByNamespace(String namespace) {
        return templates.values()
            .stream()
            .filter(template -> template.getNamespace().equals(namespace))
            .collect(Collectors.toList());
    }

    @Override
    public Template create(Template template) {
        templates.put(template.getId(), template);
        templateQueue.emit(template);

        eventPublisher.publishEvent(new CrudEvent<>(template, CrudEventType.CREATE));

        return template;
    }

    @Override
    public Template update(Template template, Template previous) {
        this
            .findById(previous.getNamespace(), previous.getId())
            .map(current -> current.validateUpdate(template))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .ifPresent(s -> {
                throw s;
            });

        templates.put(template.getId(), template);
        templateQueue.emit(template);

        eventPublisher.publishEvent(new CrudEvent<>(template, CrudEventType.UPDATE));

        return template;
    }

    @Override
    public void delete(Template template) {
        if (!templates.containsKey(template.getId())) {
            throw new IllegalStateException("Template " + template.getId() + " doesn't exists");
        }

        this.templates.remove(template.getId());
        templateQueue.emit(template.toDeleted());

        eventPublisher.publishEvent(new CrudEvent<>(template, CrudEventType.DELETE));
    }

    @Override
    public List<String> findDistinctNamespace() {
        HashSet<String> namespaces = new HashSet<>();
        for (Template t : this.findAll()) {
            namespaces.add(t.getNamespace());
        }

        ArrayList<String> namespacesList = new ArrayList<>(namespaces);
        Collections.sort(namespacesList);
        return new ArrayList<>(namespacesList);
    }
}
