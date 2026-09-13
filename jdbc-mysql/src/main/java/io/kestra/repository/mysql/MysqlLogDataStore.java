package io.kestra.repository.mysql;

import java.util.Arrays;
import java.util.Date;

import org.jooq.Condition;
import org.jooq.Field;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.plugins.ApplicationContextInitializable;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.repository.AbstractJdbcLogDataStore;

import io.micronaut.context.ApplicationContext;

/**
 * MySQL {@link LogDataStoreInterface} log store, selected by {@code kestra.logs.type: mysql}.
 * <p>
 * Deserialized from configuration by {@code LogDataStoreInterfaceFactory}, then wires its runtime
 * dependencies in {@link #init(ApplicationContext)}: a dedicated MySQL repository when
 * {@code kestra.logs.mysql.url} is set, otherwise the shared {@code @Named("logs")} repository.
 */
@Plugin
@Plugin.Id("mysql")
public class MysqlLogDataStore extends AbstractJdbcLogDataStore implements ApplicationContextInitializable {

    public MysqlLogDataStore() {
        super();
    }

    @Override
    public void init(ApplicationContext applicationContext) {
        initFrom(applicationContext, (tableConfig, wrapper) -> new MysqlRepository<>(tableConfig, wrapper));
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(
            Arrays.asList("namespace", "flow_id", "task_id", "execution_id", "taskrun_id", "trigger_id", "message", "thread"),
            query
        );
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return MysqlRepositoryUtils.formatDateField(dateField, groupType);
    }
}
