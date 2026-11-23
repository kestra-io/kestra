package io.kestra.core.models.namespaces.files;

import java.time.Instant;
import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.validations.TenantId;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Builder(toBuilder = true)
@Slf4j
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
@EqualsAndHashCode
public class NamespaceFileMetadata implements SoftDeletable<NamespaceFileMetadata>, TenantInterface, HasUID {
    @With
    @Hidden
    @TenantId
    private String tenantId;

    @NotNull
    private String namespace;

    @NotNull
    private String path;

    private String parentPath;

    // Field renamed to 'revision' but the persisted JSON key stays "version" (frozen via @JsonProperty)
    // to avoid a JSONB backfill on existing installs; see VersionJsonKeyFreezeTest.
    @NotNull
    @JsonProperty("version")
    private Integer revision;

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
    public NamespaceFileMetadata(String tenantId, String namespace, String path, String parentPath, @JsonProperty("version") Integer revision, boolean last, Long size, Instant created,
        @Nullable Instant updated,
        boolean deleted) {
        this.tenantId = tenantId;
        this.namespace = namespace;
        this.path = path;
        this.parentPath = parentPath(path);
        this.revision = revision;
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
        return withoutTrailingSlash.contains("/") ? withoutTrailingSlash.substring(0, withoutTrailingSlash.lastIndexOf("/") + 1) : null;
    }

    public static NamespaceFileMetadata of(String tenantId, NamespaceFile namespaceFile) {
        return NamespaceFileMetadata.builder()
            .tenantId(tenantId)
            .namespace(namespaceFile.namespace())
            .path(namespaceFile.filePath().toString())
            .revision(namespaceFile.revision())
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
            .revision(1)
            .build();
    }

    /**
     * Returns the highest revision of the given entries that has been deleted, or {@code 0} when none has.
     * <p>
     * Deleting a file flags the entry of its current revision in place, leaving the entries of earlier
     * revisions untouched, and re-creating the path adds a live entry above them. The deleted entry is
     * therefore a floor rather than a state of the whole file: everything up to and including it was part
     * of what the user deleted, whatever happened at the path afterwards, so no revision at or below it may
     * be served again.
     *
     * @param revisions Every entry held for one path, in any order.
     * @return The highest deleted revision, or {@code 0} if the path has never been deleted.
     */
    public static int deletedFloor(Collection<NamespaceFileMetadata> revisions) {
        return revisions.stream()
            .filter(NamespaceFileMetadata::isDeleted)
            .mapToInt(NamespaceFileMetadata::getRevision)
            .max()
            .orElse(0);
    }

    public NamespaceFileMetadata asLast() {
        Instant saveDate = Instant.now();
        return this.toBuilder().updated(saveDate).last(true).build();
    }

    @Override
    public NamespaceFileMetadata toDeleted() {
        return this.toBuilder().deleted(true).updated(Instant.now()).build();
    }

    @Override
    public String uid() {
        return IdUtils.fromParts(getTenantId(), getNamespace(), getPath(), String.valueOf(getRevision()));
    }

    @JsonIgnore
    public boolean isDirectory() {
        return this.path.endsWith("/");
    }
}
