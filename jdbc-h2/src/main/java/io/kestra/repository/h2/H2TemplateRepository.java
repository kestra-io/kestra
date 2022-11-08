package io.kestra.repository.h2;

import io.kestra.core.models.templates.Template;
import io.kestra.jdbc.repository.AbstractJdbcTemplateRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.List;

@Singleton
@H2RepositoryEnabled
public class H2TemplateRepository extends AbstractJdbcTemplateRepository {
    @Inject
    public H2TemplateRepository(ApplicationContext applicationContext) {
        super(new H2Repository<>(Template.class, applicationContext), applicationContext);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(List.of("fulltext"), query);
    }
}
