package io.kestra.core.models.assets;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.kestra.core.models.annotations.Plugin;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@NoArgsConstructor
@Plugin
public class External extends Asset {
    public static final String ASSET_TYPE = External.class.getName();

    @Builder
    @JsonCreator
    public External(
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
