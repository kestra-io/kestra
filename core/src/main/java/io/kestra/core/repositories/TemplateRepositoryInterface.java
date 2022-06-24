package io.kestra.core.repositories;

import io.micronaut.data.model.Pageable;
import io.kestra.core.models.templates.Template;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

public interface TemplateRepositoryInterface {
    Optional<Template> findById(String namespace, String id);

    List<Template> findAll();

    ArrayListTotal<Template> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String namespace
    );

    List<Template> findByNamespace(String namespace);

    Template create(Template template);

    Template update(Template template, Template previous);

    void delete(Template template);

    List<String> findDistinctNamespace();
}
