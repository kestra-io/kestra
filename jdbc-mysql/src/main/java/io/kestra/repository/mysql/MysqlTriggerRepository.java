package io.kestra.repository.mysql;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.List;

@Singleton
@MysqlRepositoryEnabled
public class MysqlTriggerRepository extends AbstractJdbcTriggerRepository {
    @Inject
    public MysqlTriggerRepository(ApplicationContext applicationContext) {
        super(new MysqlRepository<>(Trigger.class, applicationContext));
    }

    @Override
    protected Condition fullTextCondition(String query) {
        return query == null ? DSL.trueCondition() : jdbcRepository.fullTextCondition(List.of("namespace", "flow_id", "trigger_id", "execution_id"), query);
    }
}
