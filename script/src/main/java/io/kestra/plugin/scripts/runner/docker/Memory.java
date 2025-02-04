package io.kestra.plugin.scripts.runner.docker;

import io.kestra.core.models.property.Property;
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
        description = """
            Make sure to use the format `number` + `unit` (regardless of the case) without any spaces.
            The unit can be KB (kilobytes), MB (megabytes), GB (gigabytes), etc.

            Given that it's case-insensitive, the following values are equivalent:
            - `"512MB"`
            - `"512Mb"`
            - `"512mb"`
            - `"512000KB"`
            - `"0.5GB"`

            It is recommended that you allocate at least `6MB`."""
    )
    private Property<String> memory;

    @Schema(
        title = "The total amount of `memory` and `swap` that can be used by a container.",
        description = "If `memory` and `memorySwap` are set to the same value, this prevents containers from " +
            "using any swap. This is because `memorySwap` includes both the physical memory and swap space, " +
            "while `memory` is only the amount of physical memory that can be used."
    )
    private Property<String> memorySwap;

    @Schema(
        title = "A setting which controls the likelihood of the kernel to swap memory pages.",
        description = "By default, the host kernel can swap out a percentage of anonymous pages used by a " +
            "container. You can set `memorySwappiness` to a value between 0 and 100 to tune this percentage."
    )
    private Property<String> memorySwappiness;

    @Schema(
        title = "Allows you to specify a soft limit smaller than `memory` which is activated when Docker detects contention or low memory on the host machine.",
        description = "If you use `memoryReservation`, it must be set lower than `memory` for it to take precedence. " +
            "Because it is a soft limit, it does not guarantee that the container doesn’t exceed the limit."
    )
    private Property<String> memoryReservation;

    @Schema(
        title = "The maximum amount of kernel memory the container can use.",
        description = "The minimum allowed value is `4MB`. Because kernel memory cannot be swapped out, a " +
            "container which is starved of kernel memory may block host machine resources, which can have " +
            "side effects on the host machine and on other containers. " +
            "See the [kernel-memory docs](https://docs.docker.com/config/containers/resource_constraints/#--kernel-memory-details) for more details."
    )
    private Property<String> kernelMemory;

    @Schema(
        title = "By default, if an out-of-memory (OOM) error occurs, the kernel kills processes in a container.",
        description = "To change this behavior, use the `oomKillDisable` option. Only disable the OOM killer " +
            "on containers where you have also set the `memory` option. If the `memory` flag is not set, the host " +
            "can run out of memory, and the kernel may need to kill the host system’s processes to free the memory."
    )
    private Property<Boolean> oomKillDisable;
}
