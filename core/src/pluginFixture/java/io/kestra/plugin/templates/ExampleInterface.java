package io.kestra.plugin.templates;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public interface ExampleInterface {
    @PluginProperty(dynamic = true)
    @Schema(title = "Example interface")
    String getExample();
}
