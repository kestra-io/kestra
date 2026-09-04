package io.kestra.core.models.assets;

import java.time.Instant;
import java.util.*;

import org.apache.commons.lang3.ObjectUtils;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.Plugin;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.MapUtils;
import io.kestra.core.validations.TenantId;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public abstract class Asset implements HasUID, SoftDeletable<Asset>, Plugin {
    @Hidden
    @TenantId
    protected String tenantId;

    @Pattern(regexp = "^[a-z0-9][a-z0-9._-]*")
    @Size(min = 1, max = 150)
    protected String namespace;

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._:-]*")
    @Size(min = 1, max = 150)
    protected String id;

    @NotBlank
    protected String type;

    protected String displayName;

    protected String description;

    protected Map<String, Object> metadata;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<AssetAction> assetActions;

    @Nullable
    @Hidden
    private Instant created;

    @Nullable
    @Hidden
    private Instant updated;

    @Hidden
    private boolean deleted;

    public Asset(
        String tenantId,
        String namespace,
        String id,
        String type,
        String displayName,
        String description,
        Map<String, Object> metadata,
        Instant created,
        Instant updated,
        boolean deleted) {
        this.tenantId = tenantId;
        this.namespace = namespace;
        this.id = id;
        this.type = type;
        this.displayName = displayName;
        this.description = description;
        this.metadata = Optional.ofNullable(metadata).map(HashMap::new).orElse(new HashMap<>());
        Instant now = Instant.now();
        this.created = Optional.ofNullable(created).orElse(now);
        this.updated = Optional.ofNullable(updated).orElse(now);
        this.deleted = deleted;
    }

    /**
     * Merges this asset over {@code previousAsset}, which is {@code null} on creation.
     *
     * @param previousAsset the stored asset this one is merged over, or {@code null} when creating.
     * @param allowTypeChange whether the incoming type wins over the stored one; either falls back to the other
     *                        when null, so creation (no previous asset) keeps the incoming type either way.
     * @return this asset, merged.
     */
    public <T extends Asset> T toUpdated(T previousAsset, boolean allowTypeChange) {
        this.created = Optional.ofNullable(previousAsset).map(Asset::getCreated).or(() -> Optional.ofNullable(this.created)).orElseGet(Instant::now);
        this.updated = Instant.now();

        String previousType = Optional.ofNullable(previousAsset).map(Asset::getType).orElse(null);
        this.type = allowTypeChange
            ? ObjectUtils.firstNonNull(this.type, previousType)
            : ObjectUtils.firstNonNull(previousType, this.type);
        // The namespace of an existing asset is immutable, as AssetsController.updateAsset already enforces
        this.namespace = Optional.ofNullable(previousAsset).map(Asset::getNamespace).orElse(this.namespace);
        this.displayName = Optional.ofNullable(this.displayName).or(() -> Optional.ofNullable(previousAsset).map(Asset::getDisplayName)).orElse(null);
        this.description = Optional.ofNullable(this.description).or(() -> Optional.ofNullable(previousAsset).map(Asset::getDescription)).orElse(null);
        Map<String, Object> incomingMetadata = Optional.ofNullable(this.metadata).orElse(new HashMap<>());
        Map<String, Object> previousMetadata = Optional.ofNullable(previousAsset).map(Asset::getMetadata).orElse(null);
        if (previousMetadata == null) {
            this.metadata = incomingMetadata;
        } else {
            Map<String, Object> mergedMetadata = MapUtils.mergeWithNullableValues(previousMetadata, incomingMetadata);
            incomingMetadata.forEach((key, value) -> {
                if (value == null) {
                    mergedMetadata.remove(key);
                }
            });
            this.metadata = mergedMetadata;
        }

        this.assetActions = this.assetActions != null
            ? this.assetActions
            : Optional.ofNullable(previousAsset).map(Asset::getAssetActions).orElse(null);

        return (T) this;
    }

    public void setAssetActions(List<AssetAction> assetActions) {
        this.assetActions = assetActions;
    }

    @Override
    public Asset toDeleted() {
        this.deleted = true;
        return this;
    }

    @JsonAnySetter
    public void setMetadata(String name, Object value) {
        // `metadataList` is an ElasticSearch indexing-only projection of `metadata` (see EE ElasticSearchAssetRepository).
        // Fresh indices exclude it from _source, but existing/upgraded indices may still return it; never fold it back
        // into the metadata map.
        if ("metadataList".equals(name)) {
            return;
        }
        metadata.put(name, value);
    }

    @Override
    public String uid() {
        return Asset.uid(tenantId, id);
    }

    public static String uid(String tenantId, String id) {
        return IdUtils.fromParts(tenantId, id);
    }

    public Asset withTenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public Asset withNamespace(String namespace) {
        this.namespace = namespace;
        return this;
    }
}
