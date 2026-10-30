package io.kestra.worker.stores;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.controller.grpc.KVStoreServiceGrpc;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.KVMetadataStateStore;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVStoreException;
import io.kestra.core.storages.kv.KVValue;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class GrpcKVBackendTest extends AbstractGrpcMetaStoreTest {

    private static final String TENANT = "main";

    // Larger than a single chunk, so both directions actually exercise the streaming.
    private static final String LARGE_VALUE = "x".repeat(2 * 1024 * 1024 + 17);

    @Inject
    StorageInterface storage;

    @Inject
    KVMetadataStateStore kvMetadataStateStore;

    @Inject
    KVStoreServiceGrpc.KVStoreServiceStub kvStoreStub;

    @Inject
    KVStoreServiceGrpc.KVStoreServiceBlockingStub kvStoreBlockingStub;

    private GrpcKVBackend backend;

    @Override
    protected void initClientStore() {
        backend = new GrpcKVBackend(kvStoreStub, kvStoreBlockingStub, clientWorkerInfo());
    }

    @Test
    void shouldWriteAnEntryThatTheClusterStorageServes() throws Exception {
        String namespace = namespace();

        workerStore(TENANT, namespace).put("large", new KVValueAndMetadata(new KVMetadata("a description", (Instant) null), LARGE_VALUE));

        assertThat(clusterStore(namespace).getValue("large")).contains(new KVValue(LARGE_VALUE));
        assertThat(clusterStore(namespace).get("large")).hasValueSatisfying(entry -> assertThat(entry.description()).isEqualTo("a description"));
    }

    @Test
    void shouldReadAValueSetOnTheClusterStorage() throws Exception {
        String namespace = namespace();
        clusterStore(namespace).put("large", new KVValueAndMetadata(null, LARGE_VALUE));
        clusterStore(namespace).put("typed", new KVValueAndMetadata(null, Map.of("ttl", "PT5M", "count", 3)));
        clusterStore(namespace).put("duration", new KVValueAndMetadata(null, Duration.ofMinutes(5)));

        KVStore workerStore = workerStore(TENANT, namespace);

        assertThat(workerStore.getValue("large")).contains(new KVValue(LARGE_VALUE));
        assertThat(workerStore.getValue("typed")).contains(new KVValue(Map.of("ttl", "PT5M", "count", 3)));
        assertThat(workerStore.getValue("duration")).contains(new KVValue(Duration.ofMinutes(5)));
    }

    @Test
    void shouldGetListAndDeleteEntriesOfTheClusterStorage() throws Exception {
        String namespace = namespace();
        clusterStore(namespace).put("first", new KVValueAndMetadata(new KVMetadata("described", (Instant) null), "value"));
        clusterStore(namespace).put("second", new KVValueAndMetadata(null, "value"));

        KVStore workerStore = workerStore(TENANT, namespace);

        assertThat(workerStore.get("first")).hasValueSatisfying(entry -> assertThat(entry.description()).isEqualTo("described"));
        assertThat(workerStore.list()).extracting(KVEntry::key).containsExactlyInAnyOrder("first", "second");

        assertThat(workerStore.delete("first")).isTrue();
        assertThat(workerStore.delete("first")).isFalse();
        assertThat(clusterStore(namespace).get("first")).isEmpty();
    }

    @Test
    void shouldStopReadingAValueClosedBeforeItsEnd() throws Exception {
        String namespace = namespace();
        clusterStore(namespace).put("large", new KVValueAndMetadata(null, LARGE_VALUE));

        try (InputStream value = backend.getRawValue(TENANT, namespace, "large").orElseThrow()) {
            assertThat(value.readNBytes(16)).hasSize(16);
        }

        assertThat(workerStore(TENANT, namespace).getValue("large")).contains(new KVValue(LARGE_VALUE));
    }

    @Test
    void shouldReturnEmptyWhenNoValueIsStored() throws Exception {
        assertThat(workerStore(TENANT, namespace()).getValue("missing")).isEmpty();
    }

    @Test
    void shouldFailWhenTheValueExpired() throws Exception {
        String namespace = namespace();
        clusterStore(namespace).put("expired", new KVValueAndMetadata(new KVMetadata(null, Instant.now().minusSeconds(60)), "value"));

        assertThatThrownBy(() -> workerStore(TENANT, namespace).getValue("expired"))
            .isInstanceOf(ResourceExpiredException.class);
    }

    @Test
    void shouldRefuseToOverwriteWhenAskedNotTo() throws Exception {
        String namespace = namespace();
        clusterStore(namespace).put("existing", new KVValueAndMetadata(null, "first"));

        assertThatThrownBy(() -> workerStore(TENANT, namespace).put("existing", new KVValueAndMetadata(null, LARGE_VALUE), false))
            .isInstanceOf(KVStoreException.class)
            .hasMessageContaining("existing");
        assertThat(clusterStore(namespace).getValue("existing")).contains(new KVValue("first"));
    }

    @Test
    void shouldRejectAStorageLocationThatIsNotValidOnTheController() {
        // The worker validates the key too, so the relay is called directly as a misbehaving worker would.
        assertThatThrownBy(() -> backend.getRawValue(TENANT, namespace(), "../escaped"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> backend.get(TENANT, "../escaped", "key"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> backend.get("..", namespace(), "key"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> backend.list("../escaped", namespace()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> backend.delete("", namespace(), "key"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private KVStore workerStore(String tenant, String namespace) {
        return new InternalKVStore(tenant, namespace, backend);
    }

    private InternalKVStore clusterStore(String namespace) {
        return new InternalKVStore(TENANT, namespace, storage, kvMetadataStateStore);
    }

    private static String namespace() {
        return "io.kestra." + IdUtils.create().toLowerCase();
    }
}
