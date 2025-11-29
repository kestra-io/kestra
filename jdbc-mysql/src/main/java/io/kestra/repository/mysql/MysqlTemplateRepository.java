package io.kestra.repository.mysql;

import io.kestra.core.models.templates.Template;
import io.kestra.core.models.templates.TemplateEnabled;
import io.kestra.jdbc.repository.AbstractJdbcTemplateRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.Arrays;

@Singleton
@MysqlRepositoryEnabled
@TemplateEnabled
public class MysqlTemplateRepository extends AbstractJdbcTemplateRepository {
    @Inject
    public MysqlTemplateRepository(@Named("templates") MysqlRepository<Template> repository,
                                   ApplicationContext applicationContext) {
        super(repository, applicationContext);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Arrays.asList("namespace", "id"), query);
    }
}
