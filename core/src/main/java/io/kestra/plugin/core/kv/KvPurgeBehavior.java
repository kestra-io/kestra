package io.kestra.plugin.core.kv;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.kv.KVEntry;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = Version.class, name = "version"),
        @JsonSubTypes.Type(value = Key.class, name = "key"),
    }
)
@Getter
@NoArgsConstructor
@SuperBuilder
public abstract class KvPurgeBehavior {
    abstract public String getType();

    protected abstract List<KVEntry> entriesToPurge(String tenant, String namespace, KVStoreService service) throws IOException;
}
