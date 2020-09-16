package org.kestra.repository.memory;

import io.micronaut.core.value.ValueException;
import io.micronaut.data.model.Pageable;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.templates.Template;
import org.kestra.core.models.validations.ModelValidator;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.repositories.TemplateRepositoryInterface;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.ConstraintViolationException;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
@MemoryRepositoryEnabled
public class MemoryTemplateRepository implements TemplateRepositoryInterface {
    private final Map<String, Template> templates = new HashMap<>();

    @Override
    public Optional<Template> findById(String id) {
        return templates.values().stream()
            .filter(template -> template.getId().equals(id)).findFirst();
    }

    @Override
    public List<Template> findAll() {
        return new ArrayList<>(templates.values());
    }

    @Override
    public ArrayListTotal<Template> find(Optional<String> query, Pageable pageable) {
        if (pageable.getNumber() < 1) {
            throw new ValueException("Page cannot be < 1");
        }

        List<Template> filteredTemplates = templates
            .values()
            .stream()
            .collect(Collectors.toList());
        System.out.println("templates here" + templates.toString());

        return ArrayListTotal.of(pageable, filteredTemplates);
    }

    @Override
    public Template create(Template template) {
         templates.put(template.getId(), template);
         return template;
    }

    @Override
    public Template update(Template template, Template previous) {
        if (templates.containsKey(template.getId())) {
            templates.put(template.getId(), template);
            return template;
        } else {
            throw new IllegalStateException("Template " + template.getId() + " doesn't exists");
        }
    }

    @Override
    public void delete(Template template) {
        if (templates.containsKey(template.getId())) {
            templates.remove(template.getId());
        } else {
            throw new IllegalStateException("Template " + template.getId() + " doesn't exists");
        }
    }
}
