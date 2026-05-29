package io.kestra.plugin.core.dashboard.data;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.dashboards.filters.In;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardDataGlobalFiltersTest {
    @Test
    void shouldMapNamespaceInFilterForTriggers() {
        QueryFilter namespaceInFilter = QueryFilter.builder()
            .field(QueryFilter.Field.NAMESPACE)
            .operation(QueryFilter.Op.IN)
            .value(List.of("company.team"))
            .build();

        ITriggers iTriggers = new ITriggers() {
        };

        var where = iTriggers.whereWithGlobalFilters(List.of(namespaceInFilter), null, null, null);

        assertThat(where).hasSize(1);
        assertThat(where.get(0)).isInstanceOf(In.class);

        In<?> inFilter = (In<?>) where.get(0);
        assertThat(((Enum<?>) inFilter.getField()).name()).isEqualTo(ITriggers.Fields.NAMESPACE.name());
        assertThat(inFilter.getValues()).containsExactly("company.team");
    }

    @Test
    void shouldMapNamespaceInFilterForMetrics() {
        QueryFilter namespaceInFilter = QueryFilter.builder()
            .field(QueryFilter.Field.NAMESPACE)
            .operation(QueryFilter.Op.IN)
            .value(List.of("company.team"))
            .build();

        IMetrics iMetrics = new IMetrics() {
        };

        var where = iMetrics.whereWithGlobalFilters(List.of(namespaceInFilter), null, null, null);

        assertThat(where).hasSize(1);
        assertThat(where.get(0)).isInstanceOf(In.class);

        In<?> inFilter = (In<?>) where.get(0);
        assertThat(((Enum<?>) inFilter.getField()).name()).isEqualTo(IMetrics.Fields.NAMESPACE.name());
        assertThat(inFilter.getValues()).containsExactly("company.team");
    }

    @Test
    void shouldExpandLevelGreaterThanOrEqualToForLogs() {
        QueryFilter levelFilter = QueryFilter.builder()
            .field(QueryFilter.Field.LEVEL)
            .operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
            .value(Level.INFO)
            .build();

        ILogs iLogs = new ILogs() {
        };

        var where = iLogs.whereWithGlobalFilters(List.of(levelFilter), null, null, null);

        assertThat(where).hasSize(1);
        assertThat(where.get(0)).isInstanceOf(In.class);

        In<?> inFilter = (In<?>) where.get(0);
        assertThat(((Enum<?>) inFilter.getField()).name()).isEqualTo(ILogs.Fields.LEVEL.name());
        assertThat(inFilter.getValues()).containsExactlyInAnyOrder("INFO", "WARN", "ERROR");
    }

    @Test
    void shouldExpandLevelLessThanOrEqualToForLogs() {
        QueryFilter levelFilter = QueryFilter.builder()
            .field(QueryFilter.Field.LEVEL)
            .operation(QueryFilter.Op.LESS_THAN_OR_EQUAL_TO)
            .value(Level.INFO)
            .build();

        ILogs iLogs = new ILogs() {
        };

        var where = iLogs.whereWithGlobalFilters(List.of(levelFilter), null, null, null);

        assertThat(where).hasSize(1);
        assertThat(where.get(0)).isInstanceOf(In.class);

        In<?> inFilter = (In<?>) where.get(0);
        assertThat(((Enum<?>) inFilter.getField()).name()).isEqualTo(ILogs.Fields.LEVEL.name());
        assertThat(inFilter.getValues()).containsExactlyInAnyOrder("TRACE", "DEBUG", "INFO");
    }

    @Test
    void shouldAcceptStringLevelValueForLogs() {
        QueryFilter levelFilter = QueryFilter.builder()
            .field(QueryFilter.Field.LEVEL)
            .operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
            .value("WARN")
            .build();

        ILogs iLogs = new ILogs() {
        };

        var where = iLogs.whereWithGlobalFilters(List.of(levelFilter), null, null, null);

        In<?> inFilter = (In<?>) where.get(0);
        assertThat(inFilter.getValues()).containsExactlyInAnyOrder("WARN", "ERROR");
    }

    @Test
    void shouldRejectUnsupportedLevelOperationForLogs() {
        QueryFilter levelFilter = QueryFilter.builder()
            .field(QueryFilter.Field.LEVEL)
            .operation(QueryFilter.Op.EQUALS)
            .value(Level.INFO)
            .build();

        ILogs iLogs = new ILogs() {
        };

        assertThatThrownBy(() -> iLogs.whereWithGlobalFilters(List.of(levelFilter), null, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("LEVEL");
    }

    @Test
    void shouldComposeLevelWithNamespaceAndFlowFiltersForLogs() {
        List<QueryFilter> filters = List.of(
            QueryFilter.builder()
                .field(QueryFilter.Field.NAMESPACE)
                .operation(QueryFilter.Op.IN)
                .value(List.of("company.team"))
                .build(),
            QueryFilter.builder()
                .field(QueryFilter.Field.FLOW_ID)
                .operation(QueryFilter.Op.IN)
                .value(List.of("my-flow"))
                .build(),
            QueryFilter.builder()
                .field(QueryFilter.Field.LEVEL)
                .operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
                .value(Level.WARN)
                .build()
        );

        ILogs iLogs = new ILogs() {
        };

        var where = iLogs.whereWithGlobalFilters(filters, null, null, null);

        assertThat(where).hasSize(3);
        assertThat(where).anyMatch(f -> ((Enum<?>) f.getField()).name().equals(ILogs.Fields.NAMESPACE.name()));
        assertThat(where).anyMatch(f -> ((Enum<?>) f.getField()).name().equals(ILogs.Fields.FLOW_ID.name()));

        In<?> levelIn = (In<?>) where.stream()
            .filter(f -> ((Enum<?>) f.getField()).name().equals(ILogs.Fields.LEVEL.name()))
            .findFirst()
            .orElseThrow();
        assertThat(levelIn.getValues()).containsExactlyInAnyOrder("WARN", "ERROR");
    }

    @Test
    void shouldReplaceExistingLevelFilterInWhereClauseForLogs() {
        QueryFilter levelFilter = QueryFilter.builder()
            .field(QueryFilter.Field.LEVEL)
            .operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
            .value(Level.ERROR)
            .build();

        var existingLevelFilter = In.<ILogs.Fields>builder()
            .field(ILogs.Fields.LEVEL)
            .values(List.of("TRACE", "DEBUG"))
            .build();

        ILogs iLogs = new ILogs() {
        };

        var where = iLogs.whereWithGlobalFilters(List.of(levelFilter), null, null, List.of(existingLevelFilter));

        assertThat(where).hasSize(1);
        In<?> inFilter = (In<?>) where.get(0);
        assertThat(inFilter.getValues()).containsExactly("ERROR");
    }
}