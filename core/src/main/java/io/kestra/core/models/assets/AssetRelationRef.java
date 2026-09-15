package io.kestra.core.models.assets;

import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(title = "A reference to another asset of the same tenant.")
public record AssetRelationRef(
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._:-]*")
    @Size(min = 1, max = 150)
    @Schema(title = "The id of the referenced asset.") String id,

    @Nullable
    @Pattern(regexp = "^[a-z0-9][a-z0-9._-]*")
    @Size(min = 1, max = 150)
    @Schema(title = "The namespace of the referenced asset, informational: asset ids are unique per tenant.") String namespace,

    @Nullable
    @Size(max = 100)
    @Schema(title = "The role this asset plays in the relation, for example primary or backup.") String role) {
}
