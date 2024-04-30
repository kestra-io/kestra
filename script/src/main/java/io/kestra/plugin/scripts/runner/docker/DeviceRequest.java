package io.kestra.plugin.scripts.runner.docker;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

@SuperBuilder
@NoArgsConstructor
@Getter
@Schema(
    title = "A request for devices to be sent to device drivers."
)
public class DeviceRequest {
    @PluginProperty(dynamic = true)
    private String driver;

    @PluginProperty
    private Integer count;

    @PluginProperty(dynamic = true)
    private List<String> deviceIds;

    @Schema(
        title = "A list of capabilities; an OR list of AND lists of capabilities."
    )
    @PluginProperty
    private List<List<String>> capabilities;

    @Schema(
        title = "Driver-specific options, specified as key/value pairs.",
        description = "These options are passed directly to the driver."
    )
    @PluginProperty
    private Map<String, String> options;
}
