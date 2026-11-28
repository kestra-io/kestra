package io.kestra.plugin.templates.model;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Builder
@Jacksonized
@Getter
@Schema(title = "The input type")
public class Example {
    @Schema(title = "The message data")
    @PluginProperty(dynamic = true)
    private final String data;

    @Schema(title = "The message subject")
    @PluginProperty
    private final String subject;
}
