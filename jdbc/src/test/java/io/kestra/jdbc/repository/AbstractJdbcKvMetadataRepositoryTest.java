package io.kestra.jdbc.repository;

import io.kestra.core.repositories.AbstractKvMetadataRepositoryTest;
import io.kestra.jdbc.JdbcTestUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractJdbcKvMetadataRepositoryTest extends AbstractKvMetadataRepositoryTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}
