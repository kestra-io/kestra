package io.kestra.jdbc.runner;

import io.kestra.executor.AbstractServiceLivenessCoordinatorTest;
import io.kestra.jdbc.JdbcTestUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

public abstract class JdbcServiceLivenessCoordinatorTest extends AbstractServiceLivenessCoordinatorTest {
    @Inject
    private JdbcTestUtils jdbcTestUtils;

    @BeforeAll
    void initSchema() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    @Inject
    private AbstractJdbcWorkerJobRunningStateStore workerJobRunningStateStore;

    @AfterEach
    void tearDown() {
        workerJobRunningStateStore.findAll().forEach(it -> workerJobRunningStateStore.deleteByKey(it.uid()));
    }
}
