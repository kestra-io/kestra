package io.kestra.jdbc.repository;

import io.kestra.core.ai.usage.repositories.AbstractAiUsageRepositoryTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.jdbc.JdbcTestUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

/**
 * The shared repository contract against a real database. Worth the cost, since every count is a generated
 * column derived from JSON — the migration's expression is likelier to be wrong than the Java, and a mock would
 * exercise neither.
 */
@KestraTest
public abstract class AbstractJdbcAiUsageRepositoryTest extends AbstractAiUsageRepositoryTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}
