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
@Plugin
public class File extends Asset {
    public static final String ASSET_TYPE = File.class.getName();

    @Builder
    @JsonCreator
    public File(
        String tenantId,
        String namespace,
        String id,
        String displayName,
        String description,
        String system,
        String path,
        Map<String, Object> metadata,
        Instant created,
        Instant updated,
        boolean deleted
    ) {
        super(tenantId, namespace, id, ASSET_TYPE, displayName, description, metadata, created, updated, deleted);

        this.setSystem(system);
        this.setPath(path);
    }

    @JsonProperty("system")
    public String getSystem() {
        return Optional.ofNullable(metadata.get("system")).map(Object::toString).orElse(null);
    }

    @JsonProperty("path")
    public String getPath() {
        return Optional.ofNullable(metadata.get("path")).map(Object::toString).orElse(null);
    }

    public void setSystem(String system) {
        if (system != null) {
            metadata.put("system", system);
        }
    }

    public void setPath(String path) {
        if (path != null) {
            metadata.put("path", path);
        }
    }
}
