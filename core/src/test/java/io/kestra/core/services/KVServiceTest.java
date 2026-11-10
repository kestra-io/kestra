package io.kestra.core.services;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.KVMetadataStateStore;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class KVServiceTest {

    @Inject
    KVMetadataStateStore kvMetadataStateStore;

    @Inject
    KVService kvService;

    @Inject
    StorageInterface storageInterface;

    @Test
    void shouldPurgeKVStoreForSpecificVersions() throws IOException {
        KVStore kvStore = new InternalKVStore(MAIN_TENANT, IdUtils.create(), storageInterface, kvMetadataStateStore);
        String key = IdUtils.create();

        kvStore.put(key, new KVValueAndMetadata(null, "value1"));
        kvStore.put(key, new KVValueAndMetadata(null, "value2"));
        kvStore.put(key, new KVValueAndMetadata(null, "value3"));

        kvService.purge(
            MAIN_TENANT, kvStore.namespace(), List.of(
                new KVEntry(kvStore.namespace(), key, 1, null, Instant.now(), Instant.now(), null),
                new KVEntry(kvStore.namespace(), key, 3, null, Instant.now(), Instant.now(), null)
            )
        );

        List<KVEntry> kvEntries = kvService.listAll(MAIN_TENANT, kvStore.namespace());
        assertThat(kvEntries).hasSize(1);
        assertThat(kvEntries.getFirst().revision()).isEqualTo(2);
    }
}
