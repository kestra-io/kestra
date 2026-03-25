package io.kestra.plugin.core.namespace;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.kestra.core.namespace.NamespaceFileService;
import io.kestra.core.storages.NamespaceFile;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = Version.class, name = "version")
    }
)
@Getter
@NoArgsConstructor
@SuperBuilder
public abstract class FilesPurgeBehavior {
    abstract public String getType();

    protected abstract List<NamespaceFile> entriesToPurge(String tenantId, String namespace, NamespaceFileService namespaceFileService) throws IOException;
}
