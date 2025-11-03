package io.kestra.jdbc.runner;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.*;
import io.kestra.core.server.AbstractServiceLivenessCoordinatorTest;
import io.kestra.jdbc.JdbcTestUtils;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

@KestraTest(environments =  {"test", "liveness"}, startRunner = true, startWorker = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // must be per-class to allow calling once init() which took a lot of time
@Property(name = "kestra.server-type", value = "EXECUTOR")
public abstract class JdbcServiceLivenessCoordinatorTest extends AbstractServiceLivenessCoordinatorTest {
    @Inject
    private JdbcTestUtils jdbcTestUtils;

    @BeforeAll
    void initSchema() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}
