package io.kestra.plugin.scripts.runner.docker;

import io.kestra.core.models.property.Property;
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
    private Property<String> registry;

    @Schema(
        title = "The registry username."
    )
    private Property<String> username;

    @Schema(
        title = "The registry password."
    )
    private Property<String> password;

    @Schema(
        title = "The registry token."
    )
    private Property<String> registryToken;

    @Schema(
        title = "The identity token."
    )
    private Property<String> identityToken;

    @Schema(
        title = "The registry authentication.",
        description = "The `auth` field is a base64-encoded authentication string of `username:password` or a token."
    )
    private Property<String> auth;
}
