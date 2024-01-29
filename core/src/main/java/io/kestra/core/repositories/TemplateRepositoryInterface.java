package io.kestra.core.repositories;

import io.micronaut.data.model.Pageable;
import io.kestra.core.models.templates.Template;

import java.util.List;
import java.util.Optional;
import jakarta.annotation.Nullable;

public interface TemplateRepositoryInterface {
    Optional<Template> findById(String tenantId, String namespace, String id);

    List<Template> findAll(String tenantId);

    List<Template> findAllForAllTenants();

    ArrayListTotal<Template> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace
    );

    // Should normally be TemplateWithSource but it didn't exist yet
    List<Template> find(
        @Nullable String query,
        @Nullable String tenantId,
        @Nullable String namespace
    );

    List<Template> findByNamespace(String tenantId, String namespace);

    Template create(Template template);

    Template update(Template template, Template previous);

    void delete(Template template);

    List<String> findDistinctNamespace(String tenantId);
}
