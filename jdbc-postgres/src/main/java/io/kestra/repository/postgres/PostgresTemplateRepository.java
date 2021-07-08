package io.kestra.repository.postgres;

import io.kestra.core.models.templates.Template;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.jdbc.repository.AbstractTemplateRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import java.util.Collections;

@Singleton
@PostgresRepositoryEnabled
public class PostgresTemplateRepository extends AbstractTemplateRepository implements TemplateRepositoryInterface {
    @Inject
    public PostgresTemplateRepository(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(Template.class, applicationContext), applicationContext);
    }

    public ArrayListTotal<Template> find(String query, Pageable pageable) {
        SelectConditionStep<Record1<Object>> select = this.jdbcRepository
            .getDslContext()
            .select(
                DSL.field("value")
            )
            .from(this.jdbcRepository.getTable())
            .where(this.defaultFilter());

        if (query != null) {
            select.and(this.jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query));
        }

        return this.jdbcRepository.fetchPage(select, pageable);
    }
}
