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
@Plugin(aliases = TableAsset.ASSET_TYPE)
public class TableAsset extends Asset {
    public static final String ASSET_TYPE = "TABLE";

    @Builder
    @JsonCreator
    public TableAsset(
        String tenantId,
        String namespace,
        String id,
        String displayName,
        String description,
        String system,
        String database,
        String schema,
        String name,
        Map<String, Object> metadata,
        Instant created,
        Instant updated,
        boolean deleted
    ) {
        super(tenantId, namespace, id, ASSET_TYPE, displayName, description, metadata, created, updated, deleted);

        this.setSystem(system);
        this.setDatabase(database);
        this.setSchema(schema);
        this.setName(name);
    }

    @JsonProperty("system")
    public String getSystem() {
        return Optional.ofNullable(metadata.get("system")).map(Object::toString).orElse(null);
    }

    @JsonProperty("database")
    public String getDatabase() {
        return Optional.ofNullable(metadata.get("database")).map(Object::toString).orElse(null);
    }

    @JsonProperty("schema")
    public String getSchema() {
        return Optional.ofNullable(metadata.get("schema")).map(Object::toString).orElse(null);
    }

    @JsonProperty("name")
    public String getName() {
        return Optional.ofNullable(metadata.get("name")).map(Object::toString).orElse(null);
    }

    public void setSystem(String system) {
        if (system != null) {
            metadata.put("system", system);
        }
    }

    public void setDatabase(String database) {
        if (database != null) {
            metadata.put("database", database);
        }
    }

    public void setSchema(String schema) {
        if (schema != null) {
            metadata.put("schema", schema);
        }
    }

    public void setName(String name) {
        if (name != null) {
            metadata.put("name", name);
        }
    }
}
