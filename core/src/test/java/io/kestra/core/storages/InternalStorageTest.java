package io.kestra.core.storages;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.utils.IdUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest
class InternalStorageTest {

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private NamespaceFactory namespaceFactory;

    @Test
    void shouldRejectCacheWritesButNotReadsGivenNamespaceWithAnEmptySegment() throws IOException {
        InternalStorage storage = storageForNamespace("team.");
        String cacheId = IdUtils.create();

        assertThatThrownBy(() -> storage.putCacheFile(new File("cache.zip"), cacheId, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.deleteCacheFile(cacheId, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(storage.getCacheFile(cacheId, null, null)).isEmpty();
    }

    @Test
    void shouldTreatAnExpiredCacheAsAMissWithoutDeletingItGivenNamespaceWithAnEmptySegment() throws IOException, InterruptedException {
        InternalStorage storage = storageForNamespace("team.");
        String cacheId = IdUtils.create();
        URI uri = context("team.").getCacheURI(cacheId, null);
        storageInterface.put(MAIN_TENANT, "team.", uri, new ByteArrayInputStream("cached".getBytes()));
        Thread.sleep(10);

        assertThat(storage.getCacheFile(cacheId, null, Duration.ofMillis(1))).isEmpty();
        assertThat(storageInterface.exists(MAIN_TENANT, "team.", uri)).isTrue();

        storageInterface.delete(MAIN_TENANT, "team.", uri);
    }

    private InternalStorage storageForNamespace(String namespace) {
        return new InternalStorage(context(namespace), storageInterface, namespaceFactory);
    }

    private static StorageContext context(String namespace) {
        return StorageContext.forExecution(
            Execution.builder().tenantId(MAIN_TENANT).id("executionid").namespace(namespace).flowId("flowid").build()
        );
    }
}
