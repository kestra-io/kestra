package io.kestra.repository.postgres;

import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.dashboards.filters.In;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.repository.AbstractJdbcLogRepository;
import io.kestra.jdbc.services.JdbcFilterService;
import io.kestra.plugin.core.dashboard.data.Logs;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.slf4j.event.Level;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Singleton
@PostgresRepositoryEnabled
public class PostgresLogRepository extends AbstractJdbcLogRepository {
    private final JdbcFilterService filterService;
    @Inject
    public PostgresLogRepository(@Named("logs") PostgresRepository<LogEntry> repository,
                                 JdbcFilterService filterService) {
        super(repository, filterService);

        this.filterService = filterService;
    }

    @Override
    protected Condition findCondition(String query) {
        return this.jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query);
    }

    @Override
    protected Condition levelsCondition(List<Level> levels) {
        return DSL.condition("level in (" +
            levels
                .stream()
                .map(s -> "'" + s + "'::log_level")
                .collect(Collectors.joining(", ")) +
            ")");
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        switch (groupType) {
            case MONTH:
                return DSL.field("TO_CHAR({0}, 'YYYY-MM')", Date.class, DSL.field(dateField));
            case WEEK:
                return DSL.field("TO_CHAR({0}, 'IYYY-IW')", Date.class, DSL.field(dateField));
            case DAY:
                return DSL.field("DATE({0})", Date.class, DSL.field(dateField));
            case HOUR:
                return DSL.field("TO_CHAR({0}, 'YYYY-MM-DD HH24:00:00')", Date.class, DSL.field(dateField));
            case MINUTE:
                return DSL.field("TO_CHAR({0}, 'YYYY-MM-DD HH24:MI:00')", Date.class, DSL.field(dateField));
            default:
                throw new IllegalArgumentException("Unsupported GroupType: " + groupType);
        }
    }

    @Override
    protected <F extends Enum<F>> SelectConditionStep<Record> where(SelectConditionStep<Record> selectConditionStep, JdbcFilterService jdbcFilterService, List<AbstractFilter<F>> filters, Map<F, String> fieldsMapping) {
        if (!ListUtils.isEmpty(filters)) {
            // Check if descriptors contain a filter of type Logs.Fields.LEVEL and apply the custom filter "statesFilter" if present
            List<In<Logs.Fields>> levelFilters = filters.stream()
                .filter(descriptor -> descriptor.getField().equals(Logs.Fields.LEVEL) && descriptor instanceof In)
                .map(descriptor -> (In<Logs.Fields>) descriptor)
                .toList();

            if (!levelFilters.isEmpty()) {
                selectConditionStep = selectConditionStep.and(
                    levelFilter(levelFilters.stream()
                        .flatMap(levelFilter -> levelFilter.getValues().stream())
                        .map(value -> Level.valueOf(value.toString()))
                        .toList())
                );
            }

            // Remove the state filters from descriptors
            List<AbstractFilter<F>> remainingFilters = filters.stream()
                .filter(descriptor -> !descriptor.getField().equals(Logs.Fields.LEVEL) || !(descriptor instanceof In))
                .toList();

            // Use the generic method addFilters with the remaining filters
            return filterService.addFilters(selectConditionStep, fieldsMapping, remainingFilters);
        } else {
            return selectConditionStep;
        }
    }

    private Condition levelFilter(List<Level> state) {
        return DSL.cast(field("level"), String.class)
            .in(state.stream().map(Enum::name).toList());
    }
}
