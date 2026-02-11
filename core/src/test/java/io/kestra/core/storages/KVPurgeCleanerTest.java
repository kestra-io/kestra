package io.kestra.core.storages;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVPurgeCleaner;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

@MicronautTest
public class KVPurgeCleanerTest {

    @Inject
    private KVPurgeCleaner kvPurgeCleaner;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private KvMetadataRepositoryInterface kvMetadataRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @MockBean(FlowRepositoryInterface.class)
    public FlowRepositoryInterface getFlowRepository(){
        return mock(FlowRepositoryInterface.class);
    }

    @Test
    @Property(name = "kestra.kv.purge-expired.batch-size", value = "2")
    void should_purge_expired_kv_entries() throws IOException, ResourceExpiredException {
        String namespace1 = "io.kestra." + IdUtils.create();
        InternalKVStore kvStore1 = new InternalKVStore(MAIN_TENANT, namespace1, storageInterface, kvMetadataRepository);
        String expiredKey1 = "key1";
        kvStore1.put(expiredKey1, new KVValueAndMetadata(new KVMetadata(null, Instant.now().minusSeconds(1)), "expired"));
        String expiredKey12 = "key2";
        kvStore1.put(expiredKey12, new KVValueAndMetadata(new KVMetadata(null, Instant.now().minusSeconds(1)), "expired"));
        String expiredKey13 = "key3";
        kvStore1.put(expiredKey13, new KVValueAndMetadata(new KVMetadata(null, Instant.now().minusSeconds(1)), "expired"));
        String expiredKey14 = "key4";
        kvStore1.put(expiredKey14, new KVValueAndMetadata(new KVMetadata(null, Instant.now().minusSeconds(1)), "expired"));
        String key1 = IdUtils.create();
        kvStore1.put(key1, new KVValueAndMetadata(new KVMetadata(null), "present1"));

        String namespace2 = "io.kestra." + IdUtils.create();
        InternalKVStore kvStore2 = new InternalKVStore(MAIN_TENANT, namespace2, storageInterface, kvMetadataRepository);
        String expiredKey2 = IdUtils.create() + "_expired";
        kvStore2.put(expiredKey2, new KVValueAndMetadata(new KVMetadata(null, Instant.now().minusSeconds(1)), "expired"));
        String key2 = IdUtils.create();
        kvStore2.put(key2, new KVValueAndMetadata(new KVMetadata(null), "present2"));

        String namespace3 = "io.kestra." + IdUtils.create();
        InternalKVStore kvStore3 = new InternalKVStore(MAIN_TENANT, namespace3, storageInterface, kvMetadataRepository);
        String expiredKey3 = IdUtils.create() + "_expired";
        kvStore3.put(expiredKey3, new KVValueAndMetadata(new KVMetadata(null, Instant.now().minusSeconds(1)), "expired"));
        String key3 = IdUtils.create();
        kvStore3.put(key3, new KVValueAndMetadata(new KVMetadata(null), "present3"));

        when(flowRepository.findDistinctNamespace(MAIN_TENANT)).thenReturn(List.of(namespace1, namespace2, namespace3));

        kvPurgeCleaner.purgeExpired();

        List<KVEntry> kvEntries1 = kvStore1.listAll();
        assertThat(kvEntries1).hasSize(1);
        assertThat(kvStore1.getValue(kvEntries1.getFirst().key()).get().value()).isEqualTo("present1");

        List<KVEntry> kvEntries2 = kvStore2.listAll();
        assertThat(kvEntries2).hasSize(1);
        assertThat(kvStore2.getValue(kvEntries2.getFirst().key()).get().value()).isEqualTo("present2");

        List<KVEntry> kvEntries3 = kvStore3.listAll();
        assertThat(kvEntries3).hasSize(1);
        assertThat(kvStore3.getValue(kvEntries3.getFirst().key()).get().value()).isEqualTo("present3");
    }

}
