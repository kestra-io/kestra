package io.kestra.repository.postgres;

import io.kestra.core.models.flows.Flow;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepositoryTest;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
public class PostgresFlowRepositoryTest extends AbstractJdbcFlowRepositoryTest {
    @Inject
    protected PostgresFlowRepository flowRepository;
    @Override
    public void invalidFlow() {

    }

    @Test
    protected void findByQuery() {
        List<Flow> save = flowRepository.find(Pageable.from(1, 100, Sort.UNSORTED), "all", null, null, null);
        assertThat((long) save.size(), is(9L));
    }
}
