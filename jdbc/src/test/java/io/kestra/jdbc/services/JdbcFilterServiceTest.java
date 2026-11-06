package io.kestra.jdbc.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.ParamType;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.dashboards.filters.EqualTo;
import io.kestra.core.models.dashboards.filters.In;
import io.kestra.core.models.dashboards.filters.NotEqualTo;
import io.kestra.core.models.dashboards.filters.NotIn;
import io.kestra.core.models.dashboards.filters.Or;

class JdbcFilterServiceTest {
    private enum Fields {
        NAMESPACE
    }

    private static final Map<Fields, String> MAPPING = Map.of(Fields.NAMESPACE, "namespace");

    private final JdbcFilterService filterService = new JdbcFilterService();

    @Test
    void shouldKeepTenantConditionOutsideOrFilter() {
        // Given
        List<AbstractFilter<Fields>> filters = List.of(Or.<Fields> builder()
            .values(List.of(
                EqualTo.<Fields> builder().field(Fields.NAMESPACE).value("a").build(),
                EqualTo.<Fields> builder().field(Fields.NAMESPACE).value("b").build()
            ))
            .build());

        // When
        String sql = render(filters);

        // Then
        assertThat(sql).endsWith("where (tenant_id = 'tenant-a' and (false or \"namespace\" = 'a' or \"namespace\" = 'b'))");
    }

    @Test
    void shouldMatchListedValuesForInFilterInsideOrFilter() {
        // Given
        List<AbstractFilter<Fields>> filters = List.of(Or.<Fields> builder()
            .values(List.of(In.<Fields> builder().field(Fields.NAMESPACE).values(List.of("a", "b")).build()))
            .build());

        // When
        String sql = render(filters);

        // Then
        assertThat(sql).contains("\"namespace\" in ('a', 'b')").doesNotContain("not in");
    }

    @Test
    void shouldKeepNullRowsForNegatedFilters() {
        // Given
        List<AbstractFilter<Fields>> filters = List.of(
            NotEqualTo.<Fields> builder().field(Fields.NAMESPACE).value("a").build(),
            NotIn.<Fields> builder().field(Fields.NAMESPACE).values(List.of("b", "c")).build()
        );

        // When
        String sql = render(filters);

        // Then
        assertThat(sql).endsWith(
            "where (tenant_id = 'tenant-a' and (\"namespace\" <> 'a' or \"namespace\" is null) and (\"namespace\" not in ('b', 'c') or \"namespace\" is null))"
        );
    }

    @Test
    void shouldKeepNullRowsForNegatedFiltersInsideOrFilter() {
        // Given
        List<AbstractFilter<Fields>> filters = List.of(
            Or.<Fields> builder()
                .values(
                    List.of(
                        NotEqualTo.<Fields> builder().field(Fields.NAMESPACE).value("a").build(),
                        NotIn.<Fields> builder().field(Fields.NAMESPACE).values(List.of("b", "c")).build()
                    )
                )
                .build()
        );

        // When
        String sql = render(filters);

        // Then
        assertThat(sql).endsWith(
            "where (tenant_id = 'tenant-a' and (false or \"namespace\" <> 'a' or \"namespace\" is null or \"namespace\" not in ('b', 'c') or \"namespace\" is null))"
        );
    }

    private String render(List<AbstractFilter<Fields>> filters) {
        DSLContext context = DSL.using(SQLDialect.H2);
        var query = context.select().from(DSL.table("flows")).where(DSL.field("tenant_id").eq("tenant-a"));
        return filterService.addFilters(query, MAPPING, filters).getSQL(ParamType.INLINED);
    }
}
