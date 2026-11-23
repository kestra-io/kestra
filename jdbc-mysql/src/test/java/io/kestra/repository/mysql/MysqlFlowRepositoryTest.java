package io.kestra.repository.mysql;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepositoryTest;
import io.kestra.plugin.core.debug.Return;

import io.micronaut.data.model.Pageable;

import static org.assertj.core.api.Assertions.assertThat;

public class MysqlFlowRepositoryTest extends AbstractJdbcFlowRepositoryTest {
    @Test
    void shouldSearchCaseInsensitivelyButMatchExactlyOnEquals() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        FlowWithSource flow = flowRepository.create(
            GenericFlow.of(
                FlowWithSource.builder()
                    .tenantId(tenant)
                    .id("Report")
                    .namespace(TEST_NAMESPACE)
                    .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("test")).build()))
                    .build()
            )
        );
        try {
            assertThat(search(tenant, QueryFilter.Field.QUERY, QueryFilter.Op.EQUALS, "rePORT")).containsExactly("Report");
            assertThat(search(tenant, QueryFilter.Field.NAMESPACE, QueryFilter.Op.STARTS_WITH, "IO.KESTRA")).containsExactly("Report");
            assertThat(search(tenant, QueryFilter.Field.NAMESPACE, QueryFilter.Op.CONTAINS, "KESTRA.UNIT")).containsExactly("Report");
            assertThat(search(tenant, QueryFilter.Field.NAMESPACE, QueryFilter.Op.EQUALS, "IO.KESTRA.UNITTEST")).isEmpty();
        } finally {
            deleteFlow(flow);
        }
    }

    private List<String> search(String tenant, QueryFilter.Field field, QueryFilter.Op op, String value) {
        ArrayListTotal<Flow> results = flowRepository.find(
            Pageable.UNPAGED,
            tenant,
            List.of(QueryFilter.builder().field(field).operation(op).value(value).build())
        );
        return results.stream().map(Flow::getId).toList();
    }
}
