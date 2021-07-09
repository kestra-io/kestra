package io.kestra.repository.mysql;

import io.kestra.core.models.templates.Template;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.jdbc.repository.AbstractTemplateRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.Pageable;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@MysqlRepositoryEnabled
public class MysqlTemplateRepository extends AbstractTemplateRepository implements TemplateRepositoryInterface {
    @Inject
    public MysqlTemplateRepository(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(Template.class, applicationContext), applicationContext);
    }

    public ArrayListTotal<Template> find(String query, Pageable pageable) {
        SelectConditionStep<Record1<Object>> select = this.jdbcRepository
            .getDslContext()
            .select(DSL.field("value"))
            .hint("SQL_CALC_FOUND_ROWS")
            .from(this.jdbcRepository.getTable())
            .where(this.defaultFilter());

        if (query != null) {
            select.and(DSL.condition("MATCH (namespace, id) AGAINST (? IN NATURAL LANGUAGE MODE)", query));
        }

        Select<Record1<Object>> limit = this.jdbcRepository.pageable(select, pageable);

        return this.jdbcRepository.fetchPage(limit);
    }
}
