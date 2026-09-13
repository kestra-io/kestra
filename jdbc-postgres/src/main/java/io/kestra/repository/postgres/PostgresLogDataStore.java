package io.kestra.repository.postgres;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.slf4j.event.Level;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.plugins.ApplicationContextInitializable;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.repository.AbstractJdbcLogDataStore;
import io.kestra.jdbc.services.JdbcFilterService;

import io.micronaut.context.ApplicationContext;

/**
 * PostgreSQL {@link LogDataStoreInterface} log store, selected by {@code kestra.logs.type: postgres}.
 * <p>
 * Deserialized from configuration by {@code LogDataStoreInterfaceFactory}, then wires its runtime
 * dependencies in {@link #init(ApplicationContext)}: a dedicated Postgres repository when
 * {@code kestra.logs.postgres.url} is set, otherwise the shared {@code @Named("logs")} repository.
 */
@Plugin
@Plugin.Id("postgres")
public class PostgresLogDataStore extends AbstractJdbcLogDataStore implements ApplicationContextInitializable {

    public PostgresLogDataStore() {
        super();
    }

    @Override
    public void init(ApplicationContext applicationContext) {
        initFrom(applicationContext, (tableConfig, wrapper) -> new PostgresRepository<>(tableConfig, wrapper));
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query);
    }

    @Override
    protected Condition levelsCondition(List<Level> levels) {
        return PostgresLogRepositoryService.levelsCondition(levels);
    }

    @Override
    protected Condition notLevelsCondition(List<Level> levels) {
        return PostgresLogRepositoryService.notLevelsCondition(levels);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return PostgresRepositoryUtils.formatDateField(dateField, groupType);
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> where(SelectConditionStep<Record> selectConditionStep, JdbcFilterService jdbcFilterService, List<AbstractFilter<F>> filters,
        Map<F, String> fieldsMapping) {
        return PostgresLogRepositoryService.where(selectConditionStep, jdbcFilterService, filters, fieldsMapping);
    }
}
