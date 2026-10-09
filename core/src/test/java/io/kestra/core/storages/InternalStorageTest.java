package io.kestra.core.storages;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class InternalStorageTest {
    @Inject
    private StorageInterface storageInterface;

    @Inject
    private NamespaceFactory namespaceFactory;

    @Test
    void shouldReturnEmptySignedUrlWhenStorageDoesNotSupportSigning() throws IOException {
        InternalStorage storage = internalStorage(storageInterface);

        Optional<SignedUrl> signed = storage.sign(URI.create("kestra:///io/kestra/tests/file.txt"), SignedUrlCapable.Operation.GET, Duration.ofMinutes(5));

        assertThat(signed).isEmpty();
    }

    @Test
    void shouldRejectNonKestraSchemeWhenSigning() {
        InternalStorage storage = internalStorage(storageInterface);

        assertThatThrownBy(() -> storage.sign(URI.create("http://example.com/file.txt"), SignedUrlCapable.Operation.GET, Duration.ofMinutes(5)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnEmptyCopyFromWhenStorageDoesNotSupportServerSideCopy() throws IOException {
        InternalStorage storage = internalStorage(storageInterface);

        Optional<URI> copied = storage.copyFrom("copy.txt", URI.create("s3://bucket/key"));

        assertThat(copied).isEmpty();
    }

    @Test
    void shouldBuildCopyFromTargetUnderContextStorageURI() throws IOException {
        RecordingCapableStorage capable = new RecordingCapableStorage();
        InternalStorage storage = internalStorage(capable);

        Optional<URI> copied = storage.copyFrom("copy.txt", URI.create("s3://bucket/key"));

        assertThat(copied).isPresent();
        // compare on getPath(), not toString(): an empty-host URI's toString() renders as "///path" but
        // getHost() then reads back null, so re-deriving the target URI from the base loses the "//" marker
        assertThat(capable.capturedTarget.getPath()).startsWith(storage.getContextBaseURI().getPath());
        assertThat(capable.capturedTarget.getPath()).endsWith("/copy.txt");
    }

    private InternalStorage internalStorage(StorageInterface storage) {
        StorageContext context = StorageContext.forFlow(
            Flow.builder().namespace("io.kestra.tests").id("internal-storage-test").tenantId(MAIN_TENANT).build()
        );
        return new InternalStorage(null, context, storage, null, namespaceFactory);
    }

    private static class RecordingCapableStorage extends NoopStorageInterface implements ServerSideCopyCapable {
        URI capturedTarget;

        @Override
        public Optional<URI> copyFrom(String tenantId, @Nullable String namespace, URI target, URI source) {
            this.capturedTarget = target;
            return Optional.of(target);
        }
    }
}
