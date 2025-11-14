package io.kestra.repository.h2;

import io.kestra.core.models.templates.Template;
import io.kestra.core.models.templates.TemplateEnabled;
import io.kestra.core.queues.QueueService;
import io.kestra.jdbc.repository.AbstractJdbcTemplateRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.List;

@Singleton
@H2RepositoryEnabled
@TemplateEnabled
public class H2TemplateRepository extends AbstractJdbcTemplateRepository {
    @Inject
    public H2TemplateRepository(@Named("templates") H2Repository<Template> repository,
                                QueueService queueService,
                                ApplicationContext applicationContext) {
        super(repository, queueService, applicationContext);
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(List.of("fulltext"), query);
    }
}
