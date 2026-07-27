package io.kestra.repository.h2;

import java.util.Date;
import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.plugins.ApplicationContextInitializable;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.utils.DateUtils;
import io.kestra.jdbc.repository.AbstractJdbcLogDataStore;

import io.micronaut.context.ApplicationContext;

/**
 * H2 {@link LogDataStoreInterface} log store, selected by {@code kestra.logs.type: h2}.
 * <p>
 * Deserialized from configuration by {@code LogDataStoreInterfaceFactory}, then wires its runtime
 * dependencies in {@link #init(ApplicationContext)}: a dedicated H2 repository when
 * {@code kestra.logs.h2.url} is set, otherwise the shared {@code @Named("logs")} repository.
 */
@Plugin
@Plugin.Id("h2")
public class H2LogDataStore extends AbstractJdbcLogDataStore implements ApplicationContextInitializable {

    public H2LogDataStore() {
        super();
    }

    @Override
    public void init(ApplicationContext applicationContext) {
        initFrom(applicationContext, (tableConfig, wrapper) -> new H2Repository<>(tableConfig, wrapper));
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(List.of("fulltext"), query);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        return H2RepositoryUtils.formatDateField(dateField, groupType);
    }
}
