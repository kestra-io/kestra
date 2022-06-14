package io.kestra.repository.postgres;

import io.kestra.core.models.templates.Template;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.jdbc.repository.AbstractJdbcTemplateRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.Collections;

@Singleton
@PostgresRepositoryEnabled
public class PostgresTemplateRepository extends AbstractJdbcTemplateRepository implements TemplateRepositoryInterface {
    @Inject
    public PostgresTemplateRepository(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(Template.class, applicationContext), applicationContext);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query);
    }
}
