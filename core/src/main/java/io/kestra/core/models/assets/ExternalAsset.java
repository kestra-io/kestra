package io.kestra.core.models.assets;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.kestra.core.models.annotations.Plugin;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@NoArgsConstructor
@Plugin(aliases = ExternalAsset.ASSET_TYPE)
public class ExternalAsset extends Asset {
    public static final String ASSET_TYPE = "EXTERNAL";

    @Builder
    @JsonCreator
    public ExternalAsset(
        String tenantId,
        String namespace,
        String id,
        String displayName,
        String description,
        Map<String, Object> metadata,
        Instant created,
        Instant updated,
        boolean deleted
    ) {
        super(tenantId, namespace, id, ASSET_TYPE, displayName, description, metadata, created, updated, deleted);
    }
}
