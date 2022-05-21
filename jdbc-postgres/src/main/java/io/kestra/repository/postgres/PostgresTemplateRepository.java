package io.kestra.repository.postgres;

import io.kestra.core.models.templates.Template;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.jdbc.repository.AbstractTemplateRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresTemplateRepository extends AbstractTemplateRepository implements TemplateRepositoryInterface {
    @Inject
    public PostgresTemplateRepository(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(Template.class, applicationContext), applicationContext);
    }
}
