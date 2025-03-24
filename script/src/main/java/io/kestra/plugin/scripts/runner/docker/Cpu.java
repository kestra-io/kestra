package io.kestra.plugin.scripts.runner.docker;

import io.kestra.core.models.property.Property;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@Getter
public class Cpu {
    @Schema(
        title = "The maximum amount of CPU resources a container can use.",
        description = "Make sure to set that to a numeric value e.g. `cpus: \"1.5\"` or `cpus: \"4\"` or For instance, if the host machine has two CPUs and you set `cpus: \"1.5\"`, the container is guaranteed **at most** one and a half of the CPUs."
    )
    private Property<Long> cpus;
}
