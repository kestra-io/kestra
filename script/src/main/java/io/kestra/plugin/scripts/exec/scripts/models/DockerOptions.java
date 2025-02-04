package io.kestra.plugin.scripts.exec.scripts.models;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.scripts.runner.docker.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@Getter
public class DockerOptions {
    @Schema(
        title = "Docker API URI."
    )
    @PluginProperty(dynamic = true)
    private String host;

    @Schema(
        title = "Docker configuration file.",
        description = "Docker configuration file that can set access credentials to private container registries. Usually located in `~/.docker/config.json`.",
        anyOf = {String.class, Map.class}
    )
    @PluginProperty(dynamic = true)
    private Object config;

    @Schema(
        title = "Credentials for a private container registry."
    )
    @PluginProperty(dynamic = true)
    private Credentials credentials;

    @Schema(
        title = "Docker image to use."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    protected String image;

    @Schema(
        title = "User in the Docker container."
    )
    @PluginProperty(dynamic = true)
    protected String user;

    @Schema(
        title = "Docker entrypoint to use."
    )
    @PluginProperty(dynamic = true)
    protected List<String> entryPoint;

    @Schema(
        title = "Extra hostname mappings to the container network interface configuration."
    )
    @PluginProperty(dynamic = true)
    protected List<String> extraHosts;

    @Schema(
        title = "Docker network mode to use e.g. `host`, `none`, etc."
    )
    @PluginProperty(dynamic = true)
    protected String networkMode;

    @Schema(
        title = "List of volumes to mount.",
        description = "Must be a valid mount expression as string, example : `/home/user:/app`.\n\n" +
            "Volumes mount are disabled by default for security reasons; you must enable them on server configuration by setting `kestra.tasks.scripts.docker.volume-enabled` to `true`."
    )
    @PluginProperty(dynamic = true)
    protected List<String> volumes;

    @Builder.Default
    protected Property<PullPolicy> pullPolicy = Property.of(PullPolicy.ALWAYS);

    @Schema(
        title = "A list of device requests to be sent to device drivers."
    )
    @PluginProperty
    protected List<DeviceRequest> deviceRequests;

    @Schema(
        title = "Limits the CPU usage to a given maximum threshold value.",
        description = "By default, each container’s access to the host machine’s CPU cycles is unlimited. " +
            "You can set various constraints to limit a given container’s access to the host machine’s CPU cycles."
    )
    @PluginProperty
    protected Cpu cpu;

    @Schema(
        title = "Limits memory usage to a given maximum threshold value.",
        description = "Docker can enforce hard memory limits, which allow the container to use no more than a " +
            "given amount of user or system memory, or soft limits, which allow the container to use as much " +
            "memory as it needs unless certain conditions are met, such as when the kernel detects low memory " +
            "or contention on the host machine. Some of these options have different effects when used alone or " +
            "when more than one option is set."
    )
    @PluginProperty
    protected Memory memory;

    @Schema(
        title = "Size of `/dev/shm` in bytes.",
        description = "The size must be greater than 0. If omitted, the system uses 64MB."
    )
    @PluginProperty(dynamic = true)
    private String shmSize;

    @Schema(
        title = "Give extended privileges to this container."
    )
    private Property<Boolean> privileged;

    @Deprecated
    public void setDockerHost(String host) {
        this.host = host;
    }

    @Deprecated
    public void setDockerConfig(String config) {
        this.config = config;
    }
}
