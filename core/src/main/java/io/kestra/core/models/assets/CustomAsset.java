package io.kestra.core.models.assets;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.kestra.core.models.annotations.Plugin;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@NoArgsConstructor
@Plugin
public class CustomAsset extends Asset {
    @Builder
    @JsonCreator
    public CustomAsset(
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
        super(tenantId, namespace, id, type, displayName, description, metadata, created, updated, deleted);
    }
}
