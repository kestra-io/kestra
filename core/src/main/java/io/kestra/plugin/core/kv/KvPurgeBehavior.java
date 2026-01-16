package io.kestra.plugin.core.kv;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVStore;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.IOException;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Version.class, name = "version"),
    @JsonSubTypes.Type(value = Key.class, name = "key"),
})
@Getter
@NoArgsConstructor
@SuperBuilder
@Introspected
public abstract class KvPurgeBehavior {
    abstract public String getType();

    protected abstract List<KVEntry> entriesToPurge(KVStore kvStore) throws IOException;
}
