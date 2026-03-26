package io.kestra.repository.mysql;

import java.sql.Timestamp;
import java.util.*;

import org.jooq.Condition;
import org.jooq.Field;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.repository.AbstractJdbcExecutionRepository;
import io.kestra.jdbc.services.JdbcFilterService;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlExecutionRepository extends AbstractJdbcExecutionRepository {
    @Inject
    public MysqlExecutionRepository(@Named("executions") MysqlRepository<Execution> repository,
        ApplicationContext applicationContext,
        JdbcFilterService filterService) {
        super(repository, applicationContext, filterService);
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
        return MysqlExecutionRepositoryService.findCondition(this.jdbcRepository, query, labels);
    }

    @Override
    public Condition findLabelCondition(Either<Map<?, ?>, String> input, QueryFilter.Op operation) {
        return MysqlExecutionRepositoryService.findLabelCondition(input, operation);
    }

    @Override
    protected Field<Integer> weekFromTimestamp(Field<Timestamp> timestampField) {
        return this.jdbcRepository.weekFromTimestamp(timestampField);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return MysqlRepositoryUtils.formatDateField(dateField, groupType);
    }
}
