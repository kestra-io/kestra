package io.kestra.repository.postgres;

import io.kestra.core.models.templates.Template;
import io.kestra.core.models.templates.TemplateEnabled;
import io.kestra.jdbc.repository.AbstractJdbcTemplateRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.Collections;

@Singleton
@PostgresRepositoryEnabled
@TemplateEnabled
public class PostgresTemplateRepository extends AbstractJdbcTemplateRepository {
    @Inject
    public PostgresTemplateRepository(@Named("templates") PostgresRepository<Template> repository,
                                      ApplicationContext applicationContext) {
        super(repository, applicationContext);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query);
    }
}
