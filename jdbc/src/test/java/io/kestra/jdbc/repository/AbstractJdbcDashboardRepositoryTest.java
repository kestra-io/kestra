package io.kestra.jdbc.repository;

import io.kestra.core.repositories.DashboardRepositoryInterface;
import io.kestra.jdbc.JdbcTestUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;


public abstract class AbstractJdbcDashboardRepositoryTest extends io.kestra.core.repositories.AbstractDashboardRepositoryTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;
    @Inject
    private DashboardRepositoryInterface dashboardRepository;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

}