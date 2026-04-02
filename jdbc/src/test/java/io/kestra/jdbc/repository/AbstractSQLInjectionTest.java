package io.kestra.jdbc.repository;

import io.kestra.core.models.Label;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.kestra.core.repositories.AbstractExecutionRepositoryTest.builder;
import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public abstract class AbstractSQLInjectionTest {
    private static final String TEST_NAMESPACE = "io.kestra.unittest";

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    void executionLabelFilterShouldResistSqlInjection() {
        var tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        var exec = executionRepository.save(
            builder(tenant, State.Type.RUNNING, null)
                .labels(List.of(new Label("mykey", "myvalue")))
                .build()
        );

        // QueryFilter API — EQUALS: injection in label key must return no results
        assertThat(
            executionRepository.find(Pageable.from(1, 10), tenant, List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.LABELS)
                    .operation(QueryFilter.Op.EQUALS)
                    .value(Map.of("' OR '1'='1", "anything"))
                    .build()
            ))
        ).as("EQUALS with SQL injection in label key should return no results").isEmpty();

        // QueryFilter API — EQUALS: injection in label value must return no results
        assertThat(
            executionRepository.find(Pageable.from(1, 10), tenant, List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.LABELS)
                    .operation(QueryFilter.Op.EQUALS)
                    .value(Map.of("mykey", "' OR '1'='1"))
                    .build()
            ))
        ).as("EQUALS with SQL injection in label value should return no results").isEmpty();

        // QueryFilter API — NOT_EQUALS: injection in label key; no execution has the injected key,
        // so NOT_EQUALS must return the execution (tests the Postgres jsonb_path_query_first path and MySQL JSON_SEARCH path)
        assertThat(
            executionRepository.find(Pageable.from(1, 10), tenant, List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.LABELS)
                    .operation(QueryFilter.Op.NOT_EQUALS)
                    .value(Map.of("' OR '1'='1", "anything"))
                    .build()
            ))
        ).as("NOT_EQUALS with SQL injection in label key should return the execution (literal comparison, key absent)")
            .usingRecursiveFieldByFieldElementComparatorOnFields("id")
            .containsOnly(exec);

        // Old Map-based API — injection in label key must return no results
        assertThat(
            executionRepository.find(null, tenant, null, null, null, null, null, null,
                    Map.of("' OR '1'='1", "anything"), null, null, false)
                .collectList().block()
        ).as("Old API: SQL injection in label key should return no results").isEmpty();

        // Old Map-based API — injection in label value must return no results
        assertThat(
            executionRepository.find(null, tenant, null, null, null, null, null, null,
                    Map.of("mykey", "' OR '1'='1"), null, null, false)
                .collectList().block()
        ).as("Old API: SQL injection in label value should return no results").isEmpty();
    }

    @Test
    void flowLabelFilterShouldResistSqlInjection() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        FlowWithSource flow = FlowWithSource.builder()
            .id("sql-injection-test")
            .namespace(TEST_NAMESPACE)
            .tenantId(tenant)
            .labels(Label.from(Map.of("mykey", "myvalue")))
            .build();
        flow = flowRepository.create(GenericFlow.of(flow));

        try {
            // EQUALS: injection in label key — payload must be treated as a literal string, returning no results
            assertThat(
                flowRepository.find(Pageable.UNPAGED, tenant, List.of(
                    QueryFilter.builder()
                        .field(QueryFilter.Field.LABELS)
                        .operation(QueryFilter.Op.EQUALS)
                        .value(Map.of("' OR '1'='1", "anything"))
                        .build()
                ))
            ).as("EQUALS with SQL injection in label key should return no results").isEmpty();

            // EQUALS: injection in label value — payload must be treated as a literal string, returning no results
            assertThat(
                flowRepository.find(Pageable.UNPAGED, tenant, List.of(
                    QueryFilter.builder()
                        .field(QueryFilter.Field.LABELS)
                        .operation(QueryFilter.Op.EQUALS)
                        .value(Map.of("mykey", "' OR '1'='1"))
                        .build()
                ))
            ).as("EQUALS with SQL injection in label value should return no results").isEmpty();

            // NOT_EQUALS: injection in label key — no flow has the injected key, so NOT_EQUALS must return the flow
            // (tests the Postgres jsonb_path_query_first path and MySQL JSON_SEARCH path)
            assertThat(
                flowRepository.find(Pageable.UNPAGED, tenant, List.of(
                    QueryFilter.builder()
                        .field(QueryFilter.Field.LABELS)
                        .operation(QueryFilter.Op.NOT_EQUALS)
                        .value(Map.of("' OR '1'='1", "anything"))
                        .build()
                ))
            ).as("NOT_EQUALS with SQL injection in label key should return the flow (literal comparison, key absent)")
                .hasSize(1)
                .extracting(Flow::getId)
                .containsExactly("sql-injection-test");
        } finally {
            deleteFlow(flow);
        }
    }

    private void deleteFlow(Flow flow) {
        if (flow == null) {
            return;
        }
        flowRepository
            .findByIdWithSource(flow.getTenantId(), flow.getNamespace(), flow.getId())
            .ifPresent(delete -> flowRepository.delete(flow.toBuilder().revision(null).build()));
    }
}
