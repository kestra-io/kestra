package io.kestra.webserver.configuration;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.QueryFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryFilterConfigurationTest {

    @Test
    void shouldClampGlobalsBelowFloor() {
        // GIVEN / WHEN — clamping happens in the canonical constructor
        QueryFilterConfiguration config = new QueryFilterConfiguration(1, 5, null);

        // THEN — clamped up to the floor
        assertEquals(QueryFilterConfiguration.FLOOR_DEPTH, config.maxDepth());
        assertEquals(QueryFilterConfiguration.FLOOR_WIDTH, config.maxWidth());
    }

    @Test
    void shouldNotClampGlobalsAtOrAboveFloor() {
        // GIVEN / WHEN
        QueryFilterConfiguration config = new QueryFilterConfiguration(10, 100, null);

        // THEN — unchanged
        assertEquals(10, config.maxDepth());
        assertEquals(100, config.maxWidth());
    }

    @Test
    void shouldClampPerResourceBelowFloor() {
        // GIVEN
        QueryFilterConfiguration.ResourceLimits limits = new QueryFilterConfiguration.ResourceLimits(1, 5);

        // WHEN
        QueryFilterConfiguration config = new QueryFilterConfiguration(
            QueryFilterConfiguration.FLOOR_DEPTH,
            QueryFilterConfiguration.FLOOR_WIDTH,
            Map.of("EXECUTION", limits)
        );

        // THEN — per-Resource values clamped to floor
        QueryFilterConfiguration.ResourceLimits clamped = config.resources().get("EXECUTION");
        assertEquals(QueryFilterConfiguration.FLOOR_DEPTH, clamped.maxDepth());
        assertEquals(QueryFilterConfiguration.FLOOR_WIDTH, clamped.maxWidth());
    }

    @Test
    void shouldResolvePerResourceOverridesAndFallBackToGlobal() {
        // GIVEN — EXECUTION overrides depth only; width unset
        QueryFilterConfiguration.ResourceLimits limits = new QueryFilterConfiguration.ResourceLimits(8, null);

        QueryFilterConfiguration config = new QueryFilterConfiguration(3, 20, Map.of("EXECUTION", limits));

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
        QueryFilterConfiguration config = new QueryFilterConfiguration(5, 50, null);

        assertEquals(5, config.maxDepthFor(null));
        assertEquals(50, config.maxWidthFor(null));
    }
}
