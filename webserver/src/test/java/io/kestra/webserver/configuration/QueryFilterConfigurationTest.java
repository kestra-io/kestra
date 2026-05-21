package io.kestra.webserver.configuration;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.QueryFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryFilterConfigurationTest {

    @Test
    void shouldClampGlobalsBelowFloor() {
        // GIVEN
        QueryFilterConfiguration config = new QueryFilterConfiguration();
        config.setMaxDepth(1);
        config.setMaxWidth(5);

        // WHEN
        config.clampToFloor();

        // THEN — clamped up to the floor
        assertEquals(QueryFilterConfiguration.FLOOR_DEPTH, config.getMaxDepth());
        assertEquals(QueryFilterConfiguration.FLOOR_WIDTH, config.getMaxWidth());
    }

    @Test
    void shouldNotClampGlobalsAtOrAboveFloor() {
        // GIVEN
        QueryFilterConfiguration config = new QueryFilterConfiguration();
        config.setMaxDepth(10);
        config.setMaxWidth(100);

        // WHEN
        config.clampToFloor();

        // THEN — unchanged
        assertEquals(10, config.getMaxDepth());
        assertEquals(100, config.getMaxWidth());
    }

    @Test
    void shouldClampPerResourceBelowFloor() {
        // GIVEN
        QueryFilterConfiguration.ResourceLimits limits = new QueryFilterConfiguration.ResourceLimits();
        limits.setMaxDepth(1);
        limits.setMaxWidth(5);

        QueryFilterConfiguration config = new QueryFilterConfiguration();
        config.setResources(Map.of("EXECUTION", limits));

        // WHEN
        config.clampToFloor();

        // THEN — per-Resource values clamped to floor
        assertEquals(QueryFilterConfiguration.FLOOR_DEPTH, limits.getMaxDepth());
        assertEquals(QueryFilterConfiguration.FLOOR_WIDTH, limits.getMaxWidth());
    }

    @Test
    void shouldResolvePerResourceOverridesAndFallBackToGlobal() {
        // GIVEN — EXECUTION overrides depth only; width unset
        QueryFilterConfiguration.ResourceLimits limits = new QueryFilterConfiguration.ResourceLimits();
        limits.setMaxDepth(8);

        QueryFilterConfiguration config = new QueryFilterConfiguration();
        config.setMaxDepth(3);
        config.setMaxWidth(20);
        config.setResources(Map.of("EXECUTION", limits));

        // WHEN / THEN
        assertEquals(8, config.maxDepthFor(QueryFilter.Resource.EXECUTION));
        assertEquals(20, config.maxWidthFor(QueryFilter.Resource.EXECUTION),
            "width unset on EXECUTION override should fall back to global");
        assertEquals(3, config.maxDepthFor(QueryFilter.Resource.FLOW),
            "FLOW has no override — should use global");
        assertEquals(20, config.maxWidthFor(QueryFilter.Resource.FLOW));
    }

    @Test
    void shouldFallBackToGlobalWhenResourceIsNull() {
        QueryFilterConfiguration config = new QueryFilterConfiguration();
        config.setMaxDepth(5);
        config.setMaxWidth(50);

        assertEquals(5, config.maxDepthFor(null));
        assertEquals(50, config.maxWidthFor(null));
    }
}
