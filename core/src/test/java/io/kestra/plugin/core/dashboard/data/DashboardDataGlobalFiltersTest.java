package io.kestra.plugin.core.dashboard.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.dashboards.filters.Contains;
import io.kestra.core.models.dashboards.filters.EqualTo;
import io.kestra.core.models.dashboards.filters.In;
import io.kestra.core.models.dashboards.filters.IsNotNull;
import io.kestra.core.models.dashboards.filters.IsNull;
import io.kestra.core.models.dashboards.filters.NotContains;
import io.kestra.core.models.dashboards.filters.NotIn;
import io.kestra.core.models.dashboards.filters.Or;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardDataGlobalFiltersTest {
    @Test
    void shouldMapLabelFiltersForExecutions() {
        // Given
        Map<String, String> labelsInFilter = new LinkedHashMap<>();
        labelsInFilter.put("environment", "prod");
        labelsInFilter.put("team", "data");

        List<QueryFilter> filters = List.of(
            QueryFilter.builder()
                .field(QueryFilter.Field.LABELS)
                .operation(QueryFilter.Op.EQUALS)
                .value(Map.of("environment", "prod"))
                .build(),
            QueryFilter.builder()
                .field(QueryFilter.Field.LABELS)
                .operation(QueryFilter.Op.IN)
                .value(labelsInFilter)
                .build(),
            QueryFilter.builder()
                .field(QueryFilter.Field.LABELS)
                .operation(QueryFilter.Op.NOT_IN)
                .value(Map.of("team", List.of("platform", "data")))
                .build(),
            QueryFilter.builder()
                .field(QueryFilter.Field.LABELS)
                .operation(QueryFilter.Op.CONTAINS)
                .value(Map.of("region", "eu"))
                .build(),
            QueryFilter.builder()
                .field(QueryFilter.Field.LABELS)
                .operation(QueryFilter.Op.NOT_CONTAINS)
                .value(Map.of("region", "us"))
                .build(),
            QueryFilter.builder()
                .field(QueryFilter.Field.LABELS)
                .operation(QueryFilter.Op.IS_NULL)
                .value(Map.of("deprecated", ""))
                .build(),
            QueryFilter.builder()
                .field(QueryFilter.Field.LABELS)
                .operation(QueryFilter.Op.IS_NOT_NULL)
                .value("owner")
                .build()
        );

        IExecutions iExecutions = new IExecutions() {
        };

        // When
        var where = iExecutions.whereWithGlobalFilters(filters, null, null, null);

        // Then
        assertThat(where).hasSize(7);

        EqualTo<?> equalTo = (EqualTo<?>) where.get(0);
        assertThat(((Enum<?>) equalTo.getField()).name()).isEqualTo(IExecutions.Fields.LABELS.name());
        assertThat(equalTo.getKey()).isEqualTo("environment");
        assertThat(equalTo.getValue()).isEqualTo("prod");

        Or<?> inOr = (Or<?>) where.get(1);
        assertThat(inOr.getValues()).hasSize(2);
        In<?> firstIn = (In<?>) inOr.getValues().get(0);
        assertThat(firstIn.getKey()).isEqualTo("environment");
        assertThat(firstIn.getValues()).containsExactly("prod");
        In<?> secondIn = (In<?>) inOr.getValues().get(1);
        assertThat(secondIn.getKey()).isEqualTo("team");
        assertThat(secondIn.getValues()).containsExactly("data");

        NotIn<?> notIn = (NotIn<?>) where.get(2);
        assertThat(notIn.getKey()).isEqualTo("team");
        assertThat(notIn.getValues()).containsExactly("platform", "data");

        Contains<?> contains = (Contains<?>) where.get(3);
        assertThat(contains.getKey()).isEqualTo("region");
        assertThat(contains.getValue()).isEqualTo("eu");

        NotContains<?> notContains = (NotContains<?>) where.get(4);
        assertThat(notContains.getKey()).isEqualTo("region");
        assertThat(notContains.getValue()).isEqualTo("us");

        IsNull<?> isNull = (IsNull<?>) where.get(5);
        assertThat(isNull.getKey()).isEqualTo("deprecated");

        IsNotNull<?> isNotNull = (IsNotNull<?>) where.get(6);
        assertThat(isNotNull.getKey()).isEqualTo("owner");
    }

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
    void shouldSupportInForLogs() {
        QueryFilter levelFilter = QueryFilter.builder()
            .field(QueryFilter.Field.LEVEL)
            .operation(QueryFilter.Op.IN)
            .value(Level.INFO)
            .build();

        ILogs iLogs = new ILogs() {
        };

        var where = iLogs.whereWithGlobalFilters(List.of(levelFilter), null, null, null);

        assertThat(where).hasSize(1);
        assertThat(where.get(0)).isInstanceOf(In.class);
        In<?> inFilter = (In<?>) where.get(0);
        assertThat(((Enum<?>) inFilter.getField()).name()).isEqualTo(ILogs.Fields.LEVEL.name());
        assertThat(inFilter.getValues()).containsExactlyInAnyOrder("INFO");

        levelFilter = QueryFilter.builder()
            .field(QueryFilter.Field.LEVEL)
            .operation(QueryFilter.Op.IN)
            .value("INFO,DEBUG")
            .build();

        where = iLogs.whereWithGlobalFilters(List.of(levelFilter), null, null, null);

        assertThat(where).hasSize(1);
        assertThat(where.get(0)).isInstanceOf(In.class);
        inFilter = (In<?>) where.get(0);
        assertThat(((Enum<?>) inFilter.getField()).name()).isEqualTo(ILogs.Fields.LEVEL.name());
        assertThat(inFilter.getValues()).containsExactlyInAnyOrder("INFO", "DEBUG");
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

        var existingLevelFilter = In.<ILogs.Fields> builder()
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
