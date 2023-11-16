package io.kestra.repository.mysql;

import io.kestra.core.models.executions.Execution;
import io.kestra.jdbc.repository.AbstractJdbcExecutionRepositoryTest;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
@MicronautTest
public class MysqlExecutionRepositoryTest extends AbstractJdbcExecutionRepositoryTest {
    @Inject
    protected MysqlExecutionRepository executionRepository;
    @Test
    protected void findTaskRun() {

    }

    @Test
    protected void taskRunsDailyStatistics() {

    }

    @Test
    protected void findExecutionByQuery() {
        List<Execution> executionList = executionRepository.find(
            Pageable.from(1, 100, Sort.UNSORTED),
            "no",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

        assertThat((long) executionList.size(), is(0L));
    }
}
