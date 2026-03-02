package io.kestra.core.models.assets;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.Plugin;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.MapUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.*;

@Getter
@NoArgsConstructor
public abstract class Asset implements HasUID, SoftDeletable<Asset>, Plugin {
    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    protected String tenantId;

    @Pattern(regexp = "^[a-z0-9][a-z0-9._-]*")
    @Size(min = 1, max = 150)
    protected String namespace;

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]*")
    @Size(min = 1, max = 150)
    protected String id;

    @NotBlank
    protected String type;

    protected String displayName;

    protected String description;

    protected Map<String, Object> metadata;

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
        boolean deleted
    ) {
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

    public <T extends Asset> T toUpdated(T previousAsset) {
        this.created = Optional.ofNullable(this.created).or(() -> Optional.ofNullable(previousAsset.getCreated())).orElseGet(Instant::now);
        this.updated = Instant.now();

        // Type is immutable, if it was set before we keep it
        this.type = Optional.ofNullable(previousAsset).map(Asset::getType).orElse(this.type);
        this.displayName = Optional.ofNullable(this.displayName).or(() -> Optional.ofNullable(previousAsset).map(Asset::getDisplayName)).orElse(null);
        this.description = Optional.ofNullable(this.description).or(() -> Optional.ofNullable(previousAsset).map(Asset::getDescription)).orElse(null);
        this.metadata = Optional.ofNullable(previousAsset).map(Asset::getMetadata).orElse(null) == null
            ? this.metadata
            : MapUtils.mergeWithNullableValues(previousAsset.getMetadata(), Optional.ofNullable(this.metadata).orElse(Collections.emptyMap()));

        return (T) this;
    }

    @Override
    public Asset toDeleted() {
        this.deleted = true;
        return this;
    }

    @JsonAnySetter
    public void setMetadata(String name, Object value) {
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
}
