package io.kestra.repository.postgres;

import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.dashboards.filters.In;
import io.kestra.core.utils.ListUtils;
import io.kestra.jdbc.services.JdbcFilterService;
import io.kestra.plugin.core.dashboard.data.Logs;
import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.slf4j.event.Level;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.kestra.jdbc.repository.AbstractJdbcRepository.field;

public final class PostgresLogRepositoryService {
    private PostgresLogRepositoryService() {
        // utility class pattern
    }

    public static Condition levelsCondition(List<Level> levels) {
        return DSL.condition("level in (" +
            levels
                .stream()
                .map(s -> "'" + s + "'::log_level")
                .collect(Collectors.joining(", ")) +
            ")");
    }

    @SuppressWarnings("unchecked")
    public static <F extends Enum<F>> SelectConditionStep<org.jooq.Record> where(SelectConditionStep<Record> selectConditionStep, JdbcFilterService jdbcFilterService, List<AbstractFilter<F>> filters, Map<F, String> fieldsMapping) {
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
            return jdbcFilterService.addFilters(selectConditionStep, fieldsMapping, remainingFilters);
        } else {
            return selectConditionStep;
        }
    }

    private static Condition levelFilter(List<Level> state) {
        return DSL.cast(field("level"), String.class)
            .in(state.stream().map(Enum::name).toList());
    }
}
