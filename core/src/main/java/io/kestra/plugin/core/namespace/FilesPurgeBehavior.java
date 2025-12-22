package io.kestra.plugin.core.namespace;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFile;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.IOException;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Version.class, name = "version")
})
@Getter
@NoArgsConstructor
@SuperBuilder
@Introspected
public abstract class FilesPurgeBehavior {
    abstract public String getType();

    protected abstract List<NamespaceFile> entriesToPurge(String tenantId, Namespace namespaceStorage) throws IOException;
}
