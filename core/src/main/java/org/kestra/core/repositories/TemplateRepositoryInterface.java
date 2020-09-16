package org.kestra.core.repositories;

import io.micronaut.data.model.Pageable;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.templates.Template;

import javax.validation.ConstraintViolationException;
import java.util.List;
import java.util.Optional;

public interface TemplateRepositoryInterface {
    Optional<Template> findById(String id);

    List<Template> findAll();

    ArrayListTotal<Template> find(Optional<String> query, Pageable pageable);

    Template create(Template template);

    Template update(Template template, Template previous);

    void delete(Template template);
}
