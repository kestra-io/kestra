package io.kestra.jdbc.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.AbstractLoadedFlowRepositoryTest;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import java.util.List;
import org.junit.jupiter.api.Test;

public abstract class AbstractJdbcLoadedFlowRepositoryTest extends AbstractLoadedFlowRepositoryTest {

    @Test
    public void findSourceCode() {
        List<SearchResult<Flow>> search = flowRepository.findSourceCode(Pageable.from(1, 10, Sort.UNSORTED), "io.kestra.plugin.core.condition.MultipleCondition", TENANT, null);

        assertThat((long) search.size()).isEqualTo(2L);

        SearchResult<Flow> flow = search
            .stream()
            .filter(flowSearchResult -> flowSearchResult.getModel()
                .getId()
                .equals("trigger-multiplecondition-listener"))
            .findFirst()
            .orElseThrow();
        assertThat(flow.getFragments().getFirst()).contains("condition.MultipleCondition[/mark]");
    }

}
