package io.kestra.core.storages;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.kestra.storage.local.LocalStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KestraStorageUriAdapterTest {

    @Test
    void shouldShowPluginsTheLegacyUriAndCallersTheCanonicalUri() throws Exception {
        StorageInterface delegate = mock(StorageInterface.class);
        when(delegate.getType()).thenReturn("io.kestra.storage.local.LocalStorage");
        when(delegate.put(eq("tenant"), eq("namespace"), any(URI.class), any(StorageObject.class))).thenAnswer(invocation ->
        {
            URI seen = invocation.getArgument(2);
            assertThat(seen).isEqualTo(URI.create("kestra:///namespace/folder/file.txt"));
            assertThat(seen.getPath()).isEqualTo("/namespace/folder/file.txt");
            return new URI("kestra", "", seen.getPath(), null, null);
        });

        KestraStorageUriAdapter adapter = new KestraStorageUriAdapter(delegate);
        URI returned = adapter.put(
            "tenant",
            "namespace",
            URI.create("kestra://namespace/folder/file.txt"),
            new ByteArrayInputStream("x".getBytes())
        );

        assertThat(returned).isEqualTo(URI.create("kestra://namespace/folder/file.txt"));
        assertThat(adapter.getType()).isEqualTo("io.kestra.storage.local.LocalStorage");
    }

    @Test
    void shouldRejectTraversalBeforeCallingThePlugin() {
        StorageInterface delegate = mock(StorageInterface.class);
        KestraStorageUriAdapter adapter = new KestraStorageUriAdapter(delegate);

        assertThatThrownBy(() -> adapter.get("tenant", "namespace", URI.create("kestra://..%2F..%2Fetc/passwd")))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(delegate);
    }

    @Test
    void shouldDelegateExistsSoADirectoryIsVisible(@TempDir Path base) throws Exception {
        LocalStorage local = new LocalStorage();
        local.setBasePath(base);
        local.init();
        KestraStorageUriAdapter adapter = new KestraStorageUriAdapter(local);

        adapter.put(
            "tenant",
            "namespace",
            URI.create("kestra://namespace/folder/file.txt"),
            new ByteArrayInputStream("x".getBytes())
        );

        assertThat(adapter.exists("tenant", "namespace", URI.create("kestra://namespace/folder"))).isTrue();
        assertThat(adapter.exists("tenant", "namespace", URI.create("kestra:///namespace/folder/file.txt"))).isTrue();
        assertThat(adapter.existsInstanceResource("namespace", URI.create("kestra://missing"))).isFalse();
    }

    @Test
    void shouldDelegatePurgeAndReturnCanonicalUris() throws Exception {
        StorageInterface delegate = mock(StorageInterface.class);
        when(delegate.purgeByLastModified(eq("tenant"), eq("namespace"), any(URI.class), isNull(), isNull(), eq(true)))
            .thenAnswer(invocation -> {
                URI seen = invocation.getArgument(2);
                assertThat(seen).isEqualTo(URI.create("kestra:///namespace/folder/"));
                return List.of(new URI("kestra", "", "/namespace/folder/a b.txt", null, null));
            });

        KestraStorageUriAdapter adapter = new KestraStorageUriAdapter(delegate);
        List<URI> purged = adapter.purgeByLastModified(
            "tenant",
            "namespace",
            URI.create("kestra://namespace/folder/"),
            null,
            null,
            true
        );

        assertThat(purged).containsExactly(URI.create("kestra://namespace/folder/a%20b.txt"));
    }

    @Test
    void shouldPurgeSpacedNamesAsPercent20(@TempDir Path base) throws Exception {
        LocalStorage local = new LocalStorage();
        local.setBasePath(base);
        local.init();
        KestraStorageUriAdapter adapter = new KestraStorageUriAdapter(local);

        adapter.put("tenant", "namespace", StorageContext.toKestraUri("/namespace/folder/a b.txt"), new ByteArrayInputStream("space".getBytes()));
        adapter.put("tenant", "namespace", StorageContext.toKestraUri("/namespace/folder/a+b.txt"), new ByteArrayInputStream("plus".getBytes()));
        adapter.put("tenant", "namespace", StorageContext.toKestraUri("/namespace/folder/report#1.csv"), new ByteArrayInputStream("hash".getBytes()));
        adapter.put("tenant", "namespace", StorageContext.toKestraUri("/namespace/folder/sub dir/nested.txt"), new ByteArrayInputStream("nested".getBytes()));

        List<URI> purged = adapter.purgeByLastModified(
            "tenant",
            "namespace",
            URI.create("kestra://namespace/folder/"),
            null,
            null,
            true
        );

        assertThat(purged).containsExactlyInAnyOrder(
            URI.create("kestra://namespace/folder/a%20b.txt"),
            URI.create("kestra://namespace/folder/a+b.txt"),
            URI.create("kestra://namespace/folder/report%231.csv"),
            URI.create("kestra://namespace/folder/sub%20dir/nested.txt")
        );
        assertThat(purged).extracting(StorageContext::logicalPath).containsExactlyInAnyOrder(
            "/namespace/folder/a b.txt",
            "/namespace/folder/a+b.txt",
            "/namespace/folder/report#1.csv",
            "/namespace/folder/sub dir/nested.txt"
        );
    }
}
