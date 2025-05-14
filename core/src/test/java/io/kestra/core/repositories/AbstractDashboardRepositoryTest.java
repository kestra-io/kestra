package io.kestra.core.repositories;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.dashboards.TimeWindow;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.services.ExecutionService;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import org.codehaus.plexus.util.StringUtils;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
public abstract class AbstractDashboardRepositoryTest {
    public static final String NAMESPACE = "io.kestra.unittest";
    public static final String TENANT_ID = "tenantId";
    private static final String DASHBOARD_DESCRIPTION = "Sample dashboard";
    @Inject
    protected ExecutionService executionService;

    @Inject
    protected DashboardRepositoryInterface dashboardRepositoryInterface;
    @Inject
    protected RunContextFactory runContextFactory;

    public static Dashboard dashBoardEntry(String id, String description) {
        return Dashboard.builder()
            .id(id)
            .description(StringUtils.defaultString(description, "Default overview dashboard"))
            .tenantId(TENANT_ID)
            .title("Test Dashboard")
            .sourceCode("postGres")
            .timeWindow(TimeWindow.builder().max(Duration.ofSeconds(10)).build())
            .updated(Instant.now())
            .build();

    }

    @Test
    protected void get_givenNoRecords_shouldReturnEmpty() {
        final Optional<Dashboard> executions = dashboardRepositoryInterface.get("tenantId", DASHBOARD_DESCRIPTION);
        assertFalse(executions.isPresent());
    }

    @Test
    void save() {
        final Dashboard dashboard = dashBoardEntry("1", DASHBOARD_DESCRIPTION);
        dashboardRepositoryInterface.save(dashboard, TENANT_ID);

        final List<Dashboard> full = dashboardRepositoryInterface.findAll(TENANT_ID);
        assertFalse(full.isEmpty());
        assertEquals(1, full.size());
    }

    @Test
    void save_givenUpdatedAndPreviousDashboard_shouldSaveUpdated() {
        final Dashboard dashboard = dashBoardEntry("1", DASHBOARD_DESCRIPTION);
        dashboardRepositoryInterface.save(dashboard, TENANT_ID);

        final Optional<Dashboard> previousDashbordOptional = dashboardRepositoryInterface.get(TENANT_ID, dashboard.getId());

        assertFalse(previousDashbordOptional.isEmpty());
        final Dashboard previousDashBoard = previousDashbordOptional.get();
        final Dashboard updatedDashBoard = dashBoardEntry("1", "updatedEntry");
        dashboardRepositoryInterface.save(previousDashBoard, updatedDashBoard, TENANT_ID);

        final Optional<Dashboard> updatedDashbordOptional = dashboardRepositoryInterface.get(TENANT_ID, dashboard.getId());
        assertTrue(updatedDashbordOptional.isPresent());
        assertEquals(updatedDashBoard.getId(), updatedDashbordOptional.get().getId());
        assertEquals(updatedDashBoard.getDescription(), updatedDashbordOptional.get().getDescription());
    }

    @Test
    void findAll() {
        final Dashboard dashboard = dashBoardEntry("1", DASHBOARD_DESCRIPTION);
        dashboardRepositoryInterface.save(dashboard, TENANT_ID);

        final List<Dashboard> full = dashboardRepositoryInterface.findAll(TENANT_ID);
        assertFalse(full.isEmpty());
        assertEquals(1, full.size());
    }


    @Test
    void list() {
        dashboardRepositoryInterface.save(dashBoardEntry("1", DASHBOARD_DESCRIPTION), TENANT_ID);
        dashboardRepositoryInterface.save(dashBoardEntry("2", DASHBOARD_DESCRIPTION), TENANT_ID);
        dashboardRepositoryInterface.save(dashBoardEntry("3", DASHBOARD_DESCRIPTION), TENANT_ID);

        ArrayListTotal<Dashboard> listOfDashboards = dashboardRepositoryInterface.list(Pageable.from(1, 10), TENANT_ID, null);
        assertFalse(listOfDashboards.isEmpty());
        assertEquals(3, listOfDashboards.size());
        assertEquals("1", listOfDashboards.get(0).getId());
        assertEquals("2", listOfDashboards.get(1).getId());
        assertEquals("3", listOfDashboards.get(2).getId());


        listOfDashboards = dashboardRepositoryInterface.list(Pageable.from(1, 10), TENANT_ID, "level in ERROR");
        assertTrue(listOfDashboards.isEmpty());
    }


    @Test
    void get() {

        final Dashboard expectedDashboard = dashBoardEntry("1", DASHBOARD_DESCRIPTION);
        dashboardRepositoryInterface.save(dashBoardEntry("1", DASHBOARD_DESCRIPTION), TENANT_ID);

        Optional<Dashboard> optionalDashboard = dashboardRepositoryInterface.get(TENANT_ID, "1");
        assertTrue(optionalDashboard.isPresent());
        assertEquals(expectedDashboard.getId(), optionalDashboard.get().getId());
        assertEquals(expectedDashboard.getDescription(), optionalDashboard.get().getDescription());

        optionalDashboard = dashboardRepositoryInterface.get(TENANT_ID, "10");
        assertFalse(optionalDashboard.isPresent());
    }


    @Test
    void delete() {
        dashboardRepositoryInterface.save(dashBoardEntry("11", DASHBOARD_DESCRIPTION), TENANT_ID);
        dashboardRepositoryInterface.save(dashBoardEntry("12", DASHBOARD_DESCRIPTION), TENANT_ID);

        final Dashboard deletedDashboard = dashboardRepositoryInterface.delete(TENANT_ID, "12");
        assertEquals("12", deletedDashboard.getId());

        final Optional<Dashboard> optionalDashboard = dashboardRepositoryInterface.get(TENANT_ID, "12");
        assertFalse(optionalDashboard.isPresent());

        final List<Dashboard> full = dashboardRepositoryInterface.findAll(TENANT_ID);
        assertFalse(full.isEmpty());
        assertEquals(1, full.size());
    }

}
