package io.kestra.repository.mysql;

import io.kestra.core.models.templates.Template;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.jdbc.repository.AbstractTemplateRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.Arrays;

@Singleton
@MysqlRepositoryEnabled
public class MysqlTemplateRepository extends AbstractTemplateRepository implements TemplateRepositoryInterface {
    @Inject
    public MysqlTemplateRepository(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(Template.class, applicationContext), applicationContext);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Arrays.asList("namespace", "flow_id", "id"), query);
    }
}
