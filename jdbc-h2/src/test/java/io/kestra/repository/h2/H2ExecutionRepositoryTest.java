package io.kestra.repository.h2;

import org.junit.jupiter.api.Test;

import io.kestra.jdbc.repository.AbstractJdbcExecutionRepositoryTest;

public class H2ExecutionRepositoryTest extends AbstractJdbcExecutionRepositoryTest {
    @Test
    @Override
    protected void mappingConflict() {

    }

    @Test
    @Override
    protected void findTaskRun() {

    }

    @Test
    @Override
    protected void taskRunsDailyStatistics() {

    }
}
