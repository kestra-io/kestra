package io.kestra.plugin.scripts.runner.docker;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@Getter
@Schema(
    title = "Credentials for a private container registry."
)
public class Credentials {
    @Schema(
        title = "The registry URL.",
        description = "If not defined, the registry will be extracted from the image name."
    )
    @PluginProperty(dynamic = true)
    private String registry;

    @Schema(
        title = "The registry username."
    )
    @PluginProperty(dynamic = true)
    private String username;

    @Schema(
        title = "The registry password."
    )
    @PluginProperty(dynamic = true)
    private String password;

    @Schema(
        title = "The registry token."
    )
    @PluginProperty(dynamic = true)
    private String registryToken;

    @Schema(
        title = "The identity token."
    )
    @PluginProperty(dynamic = true)
    private String identityToken;

    @Schema(
        title = "The registry authentication.",
        description = "The `auth` field is a base64-encoded authentication string of `username:password` or a token."
    )
    @PluginProperty(dynamic = true)
    private String auth;
}
