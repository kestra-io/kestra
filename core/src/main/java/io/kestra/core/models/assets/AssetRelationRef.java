package io.kestra.core.models.assets;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A reference to another asset of the same tenant.
 *
 * @param hidden {@code true} only on a reference returned to a reader who may not see the referenced asset, in
 *        which case {@code id}, {@code namespace} and {@code role} are all null. Never stored; written back as an
 *        asset's {@code partOf}, it leaves the stored parent unchanged.
 */
@Schema(title = "A reference to another asset of the same tenant.")
public record AssetRelationRef(
    @NotBlank
    @Pattern(regexp = Asset.ID_PATTERN)
    @Size(min = 1, max = Asset.ID_MAX_LENGTH)
    @Schema(title = "The id of the referenced asset.") String id,

    @Nullable
    @Pattern(regexp = "^[a-z0-9][a-z0-9._-]*")
    @Size(min = 1, max = 150)
    @Schema(title = "The namespace of the referenced asset, informational: asset ids are unique per tenant.") String namespace,

    @Nullable
    @Size(max = 100)
    @Schema(title = "The role this asset plays in the relation, for example primary or backup.") String role,

    @Nullable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(
        title = "Set when the referenced asset is in a namespace the caller may not see, in which case the reference carries no id, namespace or role.",
        accessMode = Schema.AccessMode.READ_ONLY
    ) Boolean hidden) {

    public static final AssetRelationRef HIDDEN = new AssetRelationRef(null, null, null, true);

    public AssetRelationRef {
        id = Asset.normalizeId(id);
    }

    public AssetRelationRef(String id, @Nullable String namespace, @Nullable String role) {
        this(id, namespace, role, null);
    }
}
