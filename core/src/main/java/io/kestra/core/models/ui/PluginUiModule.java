package io.kestra.core.models.ui;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PluginUiModule(String uiModule, Map<String, Object> staticInfo, List<String> styles, PluginDistribution distribution) {
    public PluginUiModule {
        distribution = Objects.requireNonNullElse(distribution, PluginDistribution.OSS);
    }
}
