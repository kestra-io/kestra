package io.kestra.core.models.kv;

import io.kestra.core.models.DeletedInterface;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.utils.IdUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;

@Builder(toBuilder = true)
@Slf4j
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class PersistedKvMetadata implements DeletedInterface, TenantInterface, HasUID {
    @With
    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    private String tenantId;

    @NotNull
    private String namespace;

    @NotNull
    private String name;

    private String description;

    @NotNull
    private Integer version;

    @Builder.Default
    private boolean last = true;

    @Nullable
    private Instant expirationDate;

    @Nullable
    private Instant created;

    @Nullable
    private Instant updated;

    private boolean deleted;

    public static PersistedKvMetadata from(String tenantId, KVEntry kvEntry) {
        return PersistedKvMetadata.builder()
            .tenantId(tenantId)
            .namespace(kvEntry.namespace())
            .name(kvEntry.key())
            .version(kvEntry.version())
            .description(kvEntry.description())
            .created(kvEntry.creationDate())
            .updated(kvEntry.updateDate())
            .expirationDate(kvEntry.expirationDate())
            .build();
    }

    public PersistedKvMetadata asLast() {
        Instant saveDate = Instant.now();
        return this.toBuilder().created(Optional.ofNullable(this.created).orElse(saveDate)).updated(saveDate).last(true).build();
    }

    @Override
    public String uid() {
        return IdUtils.fromParts(getTenantId(), getNamespace(), getName(), getVersion().toString());
    }
}
