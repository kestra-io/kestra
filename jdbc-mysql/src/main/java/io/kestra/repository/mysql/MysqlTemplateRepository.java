package io.kestra.repository.mysql;

import io.kestra.core.models.templates.Template;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.jdbc.repository.AbstractTemplateRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlTemplateRepository extends AbstractTemplateRepository implements TemplateRepositoryInterface {
    @Inject
    public MysqlTemplateRepository(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(Template.class, applicationContext), applicationContext);
    }
}
