package io.kestra.plugin.scripts.runner.docker;

import io.kestra.core.models.property.Property;
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
    private Property<String> driver;

    private Property<Integer> count;

    private Property<List<String>> deviceIds;

    @Schema(
        title = "A list of capabilities; an OR list of AND lists of capabilities."
    )
    private Property<List<List<String>>> capabilities;

    @Schema(
        title = "Driver-specific options, specified as key/value pairs.",
        description = "These options are passed directly to the driver."
    )
    private Property<Map<String, String>> options;
}
