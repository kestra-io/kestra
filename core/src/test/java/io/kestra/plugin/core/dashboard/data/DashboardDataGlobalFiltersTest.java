package io.kestra.plugin.core.dashboard.data;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.dashboards.filters.In;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}