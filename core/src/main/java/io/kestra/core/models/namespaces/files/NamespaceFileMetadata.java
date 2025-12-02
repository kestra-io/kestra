package io.kestra.core.models.namespaces.files;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.DeletedInterface;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.utils.IdUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Builder(toBuilder = true)
@Slf4j
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
@EqualsAndHashCode
public class NamespaceFileMetadata implements DeletedInterface, TenantInterface, HasUID {
    @With
    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    private String tenantId;

    @NotNull
    private String namespace;

    @NotNull
    private String path;

    private String parentPath;

    @NotNull
    private Integer version;

    @Builder.Default
    private boolean last = true;

    @NotNull
    private Long size;

    @Builder.Default
    private Instant created = Instant.now();

    @Nullable
    private Instant updated;

    private boolean deleted;

    @JsonCreator
    public NamespaceFileMetadata(String tenantId, String namespace, String path, String parentPath, Integer version, boolean last, Long size, Instant created, @Nullable Instant updated, boolean deleted) {
        this.tenantId = tenantId;
        this.namespace = namespace;
        this.path = path;
        this.parentPath = parentPath(path);
        this.version = version;
        this.last = last;
        this.size = size;
        this.created = created;
        this.updated = updated;
        this.deleted = deleted;
    }

    public static String path(String path, boolean trailingSlash) {
        if (trailingSlash && !path.endsWith("/")) {
            return path + "/";
        } else if (!trailingSlash && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    public String path(boolean trailingSlash) {
        return path(this.path, trailingSlash);
    }

    public static String parentPath(String path) {
        String withoutTrailingSlash = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        // The parent path can't be set, it's always computed
        return withoutTrailingSlash.contains("/") ?
            withoutTrailingSlash.substring(0, withoutTrailingSlash.lastIndexOf("/") + 1) :
            null;
    }

    public static NamespaceFileMetadata of(String tenantId, NamespaceFile namespaceFile) {
        return NamespaceFileMetadata.builder()
            .tenantId(tenantId)
            .namespace(namespaceFile.namespace())
            .path(namespaceFile.path(true).toString())
            .version(namespaceFile.version())
            .build();
    }

    public static NamespaceFileMetadata of(String tenantId, String namespace, String path, FileAttributes fileAttributes) {
        return NamespaceFileMetadata.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .path(path)
            .created(Instant.ofEpochMilli(fileAttributes.getCreationTime()))
            .updated(Instant.ofEpochMilli(fileAttributes.getLastModifiedTime()))
            .size(fileAttributes.getSize())
            .version(1)
            .build();
    }

    public NamespaceFileMetadata asLast() {
        Instant saveDate = Instant.now();
        return this.toBuilder().updated(saveDate).last(true).build();
    }

    public NamespaceFileMetadata toDeleted() {
        return this.toBuilder().deleted(true).updated(Instant.now()).build();
    }

    @Override
    public String uid() {
        return IdUtils.fromParts(getTenantId(), getNamespace(), getPath(), String.valueOf(getVersion()));
    }

    @JsonIgnore
    public boolean isDirectory() {
        return this.path.endsWith("/");
    }
}
