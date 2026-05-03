package io.kestra.repository.mysql;

import java.util.Date;
import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.repository.AbstractJdbcTriggerRepository;
import io.kestra.jdbc.services.JdbcFilterService;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlTriggerRepository extends AbstractJdbcTriggerRepository {
    @Inject
    public MysqlTriggerRepository(@Named("triggers") MysqlRepository<TriggerState> repository,
        JdbcFilterService filterService) {
        super(repository, filterService);
    }

    @Override
    protected Condition fullTextCondition(String query) {
        return query == null ? DSL.noCondition() : jdbcRepository.fullTextCondition(List.of("namespace", "flow_id", "trigger_id", "execution_id"), query);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return MysqlRepositoryUtils.formatDateField(dateField, groupType);
    }
}
