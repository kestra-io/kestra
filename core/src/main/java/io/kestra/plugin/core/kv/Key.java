package io.kestra.plugin.core.kv;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVStore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@SuperBuilder
@Getter
@NoArgsConstructor
public class Key extends KvPurgeBehavior {
    @NotNull
    @JsonInclude
    @Builder.Default
    protected String type = "key";

    @Schema(
        title = "Delete only expired keys"
    )
    @Builder.Default
    private boolean expiredOnly = true;

    @Override
    protected List<KVEntry> entriesToPurge(KVStore kvStore) throws IOException {
        return kvStore.listAll().stream().filter(kv -> !expiredOnly || (kv.expirationDate() != null && kv.expirationDate().isBefore(Instant.now()))).toList();
    }
}
