package io.kestra.jdbc.repository;

import io.kestra.core.Helpers;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.Flow;
import io.kestra.jdbc.JdbcTestUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public abstract class AbstractJdbcFlowRepositoryTest extends io.kestra.core.repositories.AbstractFlowRepositoryTest {
    @Inject
    AbstractJdbcFlowRepository flowRepository;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Test
    void find() {
        List<Flow> save = flowRepository.find(Pageable.from(1, 100, Sort.of(Sort.Order.asc("id"))), null, null);
        assertThat((long) save.size(), is(Helpers.FLOWS_COUNT));

        save = flowRepository.find(Pageable.from(1, 10, Sort.UNSORTED), "trigger-multiplecondition", null);
        assertThat((long) save.size(), is(3L));
    }

    @Test
    void findSourceCode() {
        List<SearchResult<Flow>> search = flowRepository.findSourceCode(Pageable.from(1, 10, Sort.UNSORTED), "io.kestra.core.models.conditions.types.MultipleCondition", null);

        assertThat((long) search.size(), is(1L));

        SearchResult<Flow> flow = search
            .stream()
            .filter(flowSearchResult -> flowSearchResult.getModel()
                .getId()
                .equals("trigger-multiplecondition-listener"))
            .findFirst()
            .orElseThrow();
        assertThat(flow.getFragments().get(0), containsString("types.MultipleCondition[/mark]"));
    }

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
        super.init();
    }
}