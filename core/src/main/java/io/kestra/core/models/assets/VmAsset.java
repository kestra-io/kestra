package io.kestra.core.models.assets;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.kestra.core.models.annotations.Plugin;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@NoArgsConstructor
@Plugin(aliases = VmAsset.ASSET_TYPE)
public class VmAsset extends Asset {
    public static final String ASSET_TYPE = "VM";

    @Builder
    @JsonCreator
    public VmAsset(
        String tenantId,
        String namespace,
        String id,
        String displayName,
        String description,
        String provider,
        String region,
        String state,
        Map<String, Object> metadata,
        Instant created,
        Instant updated,
        boolean deleted
    ) {
        super(tenantId, namespace, id, ASSET_TYPE, displayName, description, metadata, created, updated, deleted);

        this.setProvider(provider);
        this.setRegion(region);
        this.setState(state);
    }

    @JsonProperty("provider")
    public String getProvider() {
        return Optional.ofNullable(metadata.get("provider")).map(Object::toString).orElse(null);
    }

    @JsonProperty("region")
    public String getRegion() {
        return Optional.ofNullable(metadata.get("region")).map(Object::toString).orElse(null);
    }

    @JsonProperty("state")
    public String getState() {
        return Optional.ofNullable(metadata.get("state")).map(Object::toString).orElse(null);
    }

    public void setProvider(String provider) {
        if (provider != null) {
            metadata.put("provider", provider);
        }
    }

    public void setRegion(String region) {
        if (region != null) {
            metadata.put("region", region);
        }
    }

    public void setState(String state) {
        if (state != null) {
            metadata.put("state", state);
        }
    }
}
