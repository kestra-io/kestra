package io.kestra.plugin.scripts.runner.docker;

import io.kestra.core.models.annotations.PluginProperty;
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
        description = "For instance, if the host machine has two CPUs and you set `cpus:\"1.5\"`, the container is guaranteed at most one and a half of the CPUs."
    )
    @PluginProperty
    private Long cpus;
}
