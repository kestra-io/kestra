package io.kestra.webserver.configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.kestra.core.models.QueryFilter;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configurable safety caps on the shape of {@link QueryFilter} trees produced by the binder.
 * <p>
 * Two caps are enforced at parse time:
 * <ul>
 *   <li>{@code max-depth} — how deeply nested {@code [and|or][N]} groups may go in a single URL param.</li>
 *   <li>{@code max-width} — how many children any single tree node may have.</li>
 * </ul>
 * Both caps default to a hard floor ({@value #FLOOR_DEPTH}, {@value #FLOOR_WIDTH}). Configured values
 * below the floor are clamped at construction time with a WARN-level log; the bean never exposes a value below the floor.
 * <p>
 * Per-Resource overrides are independent of the global value — they may be tighter or looser than the global cap,
 * but still subject to the same floor.
 */
@ConfigurationProperties("kestra.webserver.query-filter")
public record QueryFilterConfiguration(
    @Bindable(defaultValue = "3") int maxDepth,
    @Bindable(defaultValue = "20") int maxWidth,
    @Nullable Map<String, ResourceLimits> resources
) {
    public static final int FLOOR_DEPTH = 3;
    public static final int FLOOR_WIDTH = 20;

    private static final Logger log = LoggerFactory.getLogger(QueryFilterConfiguration.class);

    public QueryFilterConfiguration {
        if (maxDepth < FLOOR_DEPTH) {
            log.warn("kestra.webserver.query-filter.max-depth ({}) is below the floor of {} - clamping to {}",
                maxDepth, FLOOR_DEPTH, FLOOR_DEPTH);
            maxDepth = FLOOR_DEPTH;
        }
        if (maxWidth < FLOOR_WIDTH) {
            log.warn("kestra.webserver.query-filter.max-width ({}) is below the floor of {} - clamping to {}",
                maxWidth, FLOOR_WIDTH, FLOOR_WIDTH);
            maxWidth = FLOOR_WIDTH;
        }
        resources = clampResources(resources);
    }

    private static Map<String, ResourceLimits> clampResources(Map<String, ResourceLimits> resources) {
        if (resources == null || resources.isEmpty()) {
            return Map.of();
        }
        Map<String, ResourceLimits> clamped = new HashMap<>(resources.size());
        resources.forEach((key, limits) -> {
            Integer depth = limits.maxDepth();
            Integer width = limits.maxWidth();
            if (depth != null && depth < FLOOR_DEPTH) {
                log.warn("kestra.webserver.query-filter.resources.{}.max-depth ({}) is below the floor of {} - clamping to {}",
                    key, depth, FLOOR_DEPTH, FLOOR_DEPTH);
                depth = FLOOR_DEPTH;
            }
            if (width != null && width < FLOOR_WIDTH) {
                log.warn("kestra.webserver.query-filter.resources.{}.max-width ({}) is below the floor of {} - clamping to {}",
                    key, width, FLOOR_WIDTH, FLOOR_WIDTH);
                width = FLOOR_WIDTH;
            }
            clamped.put(key, new ResourceLimits(depth, width));
        });
        return Map.copyOf(clamped);
    }

    public int maxDepthFor(QueryFilter.Resource resource) {
        return lookup(resource).map(ResourceLimits::maxDepth).filter(Objects::nonNull).orElse(maxDepth);
    }

    public int maxWidthFor(QueryFilter.Resource resource) {
        return lookup(resource).map(ResourceLimits::maxWidth).filter(Objects::nonNull).orElse(maxWidth);
    }

    private Optional<ResourceLimits> lookup(QueryFilter.Resource resource) {
        if (resource == null || resources == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(resources.get(resource.name()))
            .or(() -> Optional.ofNullable(resources.get(resource.name().toLowerCase())));
    }

    public record ResourceLimits(@Nullable Integer maxDepth, @Nullable Integer maxWidth) {
    }
}
