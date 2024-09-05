package io.kestra.jdbc.repository;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcTestUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractJdbcTriggerRepositoryTest extends io.kestra.core.repositories.AbstractTriggerRepositoryTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    protected AbstractJdbcTriggerRepository repository;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    @Test
    void shouldCountForNullTenant() {
        // Given
        repository.create(Trigger
            .builder()
                .triggerId(IdUtils.create())
                .flowId(IdUtils.create())
                .namespace("io.kestra.unittest")
            .build()
        );
        // When
        int count = repository.count(null);
        // Then
        Assertions.assertEquals(1, count);
    }

    @Test
    void shouldCountForNullTenantGivenNamespace() {
        // Given
        repository.create(Trigger
            .builder()
            .triggerId(IdUtils.create())
            .flowId(IdUtils.create())
            .namespace("io.kestra.unittest")
            .namespace("io.kestra.unittest.shouldcountbynamespacefornulltenant")
            .build()
        );
        // When
        int count = repository.countForNamespace(null, "io.kestra.unittest.shouldcountbynamespacefornulltenant");
        // Then
        Assertions.assertEquals(1, count);

    }
}