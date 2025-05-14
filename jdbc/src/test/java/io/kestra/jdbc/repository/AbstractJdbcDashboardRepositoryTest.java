package io.kestra.jdbc.repository;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.repositories.DashboardRepositoryInterface;
import io.kestra.core.server.ServiceInstance;
import io.kestra.jdbc.JdbcTestUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
public class AbstractJdbcDashboardRepositoryTest extends io.kestra.core.repositories.AbstractDashboardRepositoryTest {
    @Inject
    JdbcTestUtils jdbcTestUtils;
    @Inject
    private DashboardRepositoryInterface dashboardRepository;

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    @Test
    void findAll() {
        // Given
        ServiceInstance instance = AbstractJdbcServiceInstanceRepositoryTest.Fixtures.RunningServiceInstance;

        List<Dashboard> existingDashboards = dashboardRepository.findAll(instance.uid());
        assertTrue(existingDashboards.isEmpty());
    }

}