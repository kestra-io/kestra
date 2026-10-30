package io.kestra.repository.postgres;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepositoryTest;

import io.micronaut.data.model.Pageable;

import static org.assertj.core.api.Assertions.assertThat;

public class PostgresFlowRepositoryTest extends AbstractJdbcFlowRepositoryTest {
    @Override
    public void invalidFlow() {

    }

    @Test
    void shouldFindFlowsBySubstring() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource camelCase = flowRepository.create(createTestingLogFlow(tenant, "TestFlowId", "log"));
        FlowWithSource hyphenated = flowRepository.create(createTestingLogFlow(tenant, "order-sync-daily", "log"));

        try {
            assertThat(findByQuery(tenant, "Flow"))
                .extracting(Flow::getId)
                .containsExactly("TestFlowId");
            assertThat(findByQuery(tenant, "ync"))
                .extracting(Flow::getId)
                .containsExactly("order-sync-daily");
        } finally {
            deleteFlow(camelCase);
            deleteFlow(hyphenated);
        }
    }

    private ArrayListTotal<Flow> findByQuery(String tenant, String query) {
        return flowRepository.find(
            Pageable.UNPAGED,
            tenant,
            List.of(QueryFilter.builder()
                .field(QueryFilter.Field.QUERY)
                .value(query)
                .operation(QueryFilter.Op.EQUALS)
                .build())
        );
    }
}
