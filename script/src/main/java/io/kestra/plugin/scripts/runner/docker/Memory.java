package io.kestra.plugin.scripts.runner.docker;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@Getter
public class Memory {
    @Schema(
        title = "The maximum amount of memory resources the container can use.",
        description = "It is recommended that you set the value to at least 6 megabytes."
    )
    @PluginProperty(dynamic = true)
    private String memory;

    @Schema(
        title = "The amount of memory this container is allowed to swap to disk.",
        description = "If `memory` and `memorySwap` are set to the same value, this prevents containers from " +
            "using any swap. This is because `memorySwap` is the amount of combined memory and swap that can be " +
            "used, while `memory` is only the amount of physical memory that can be used."
    )
    @PluginProperty(dynamic = true)
    private String memorySwap;

    @Schema(
        title = "The amount of memory this container is allowed to swap to disk.",
        description = "By default, the host kernel can swap out a percentage of anonymous pages used by a " +
            "container. You can set `memorySwappiness` to a value between 0 and 100, to tune this percentage."
    )
    @PluginProperty(dynamic = true)
    private String memorySwappiness;

    @Schema(
        title = "Allows you to specify a soft limit smaller than `memory` which is activated when Docker detects contention or low memory on the host machine.",
        description = "If you use `memoryReservation`, it must be set lower than `memory` for it to take precedence. " +
            "Because it is a soft limit, it does not guarantee that the container doesn’t exceed the limit."
    )
    @PluginProperty(dynamic = true)
    private String memoryReservation;

    @Schema(
        title = "The maximum amount of kernel memory the container can use.",
        description = "The minimum allowed value is 4m. Because kernel memory cannot be swapped out, a " +
            "container which is starved of kernel memory may block host machine resources, which can have " +
            "side effects on the host machine and on other containers. " +
            "See [--kernel-memory](https://docs.docker.com/config/containers/resource_constraints/#--kernel-memory-details) details."
    )
    @PluginProperty(dynamic = true)
    private String kernelMemory;

    @Schema(
        title = "By default, if an out-of-memory (OOM) error occurs, the kernel kills processes in a container.",
        description = "To change this behavior, use the `oomKillDisable` option. Only disable the OOM killer " +
            "on containers where you have also set the `memory` option. If the `memory` flag is not set, the host " +
            "can run out of memory, and the kernel may need to kill the host system’s processes to free the memory."
    )
    @PluginProperty
    private Boolean oomKillDisable;
}
