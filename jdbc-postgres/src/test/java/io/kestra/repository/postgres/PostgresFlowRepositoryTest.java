package io.kestra.repository.postgres;

import io.kestra.jdbc.repository.AbstractJdbcFlowRepositoryTest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

@MicronautTest
public class PostgresFlowRepositoryTest extends AbstractJdbcFlowRepositoryTest {
    @Override
    public void invalidFlow() {

    }
}
