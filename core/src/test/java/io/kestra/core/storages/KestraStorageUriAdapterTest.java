package io.kestra.core.storages;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.kestra.storage.local.LocalStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
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

    @Test
    void shouldKeepLegacyChildUrisWhenDefaultPurgeListsByPath() throws Exception {
        List<URI> listed = new ArrayList<>();
        List<URI> deleted = new ArrayList<>();
        StorageInterface delegate = mock(StorageInterface.class, CALLS_REAL_METHODS);
        doAnswer(invocation ->
        {
            URI uri = invocation.getArgument(2);
            listed.add(uri);
            return switch (uri.getPath()) {
                case "/namespace/folder/" -> List.of(
                    pathAttributes("a.txt", FileAttributes.FileType.File),
                    pathAttributes("sub", FileAttributes.FileType.Directory)
                );
                case "/namespace/folder/sub/" -> List.of(pathAttributes("b.txt", FileAttributes.FileType.File));
                default -> List.of();
            };
        }).when(delegate).list(eq("tenant"), eq("namespace"), any(URI.class));
        doAnswer(invocation ->
        {
            deleted.add(invocation.getArgument(2));
            return true;
        }).when(delegate).delete(eq("tenant"), eq("namespace"), any(URI.class));

        KestraStorageUriAdapter adapter = new KestraStorageUriAdapter(delegate);
        List<URI> purged = adapter.purgeByLastModified(
            "tenant",
            "namespace",
            URI.create("kestra://namespace/folder/"),
            null,
            null,
            false
        );

        assertThat(listed).containsExactly(
            URI.create("kestra:///namespace/folder/"),
            URI.create("kestra:///namespace/folder/sub/")
        );
        assertThat(deleted).containsExactly(
            URI.create("kestra:///namespace/folder/a.txt"),
            URI.create("kestra:///namespace/folder/sub/b.txt")
        );
        assertThat(deleted).allSatisfy(uri -> assertThat(uri.getAuthority()).isNull());
        assertThat(deleted).extracting(URI::getPath).containsExactly(
            "/namespace/folder/a.txt",
            "/namespace/folder/sub/b.txt"
        );
        assertThat(purged).containsExactly(
            URI.create("kestra://namespace/folder/a.txt"),
            URI.create("kestra://namespace/folder/sub/b.txt")
        );
    }

    @Test
    void shouldKeepCanonicalChildUrisWhenTheParentHasAnAuthority() throws Exception {
        StorageInterface storage = mock(StorageInterface.class, CALLS_REAL_METHODS);
        doAnswer(invocation ->
        {
            URI uri = invocation.getArgument(2);
            if (uri.equals(URI.create("kestra://namespace/folder/"))) {
                return List.of(pathAttributes("a.txt", FileAttributes.FileType.File));
            }
            return List.of();
        }).when(storage).list(eq("tenant"), eq("namespace"), any(URI.class));

        List<URI> purged = storage.purgeByLastModified(
            "tenant",
            "namespace",
            URI.create("kestra://namespace/folder/"),
            null,
            null,
            true
        );

        assertThat(purged).containsExactly(URI.create("kestra://namespace/folder/a.txt"));
    }

    @Test
    void shouldKeepAnEmptyAuthorityWhenPurgingFromTheKestraRoot() throws Exception {
        List<URI> listed = new ArrayList<>();
        StorageInterface storage = mock(StorageInterface.class, CALLS_REAL_METHODS);
        doAnswer(invocation ->
        {
            URI uri = invocation.getArgument(2);
            listed.add(uri);
            if ("/".equals(uri.getPath()) && uri.getAuthority() == null) {
                return List.of(pathAttributes("namespace", FileAttributes.FileType.Directory));
            }
            return List.of();
        }).when(storage).list(eq("tenant"), eq("namespace"), any(URI.class));

        storage.purgeByLastModified("tenant", "namespace", URI.create("kestra:///"), null, null, true);

        assertThat(listed).containsExactly(
            URI.create("kestra:///"),
            URI.create("kestra:///namespace/")
        );
    }

    private static FileAttributes pathAttributes(String fileName, FileAttributes.FileType type) {
        return new FileAttributes() {
            @Override
            public String getFileName() {
                return fileName;
            }

            @Override
            public long getLastModifiedTime() {
                return 0L;
            }

            @Override
            public long getCreationTime() {
                return 0L;
            }

            @Override
            public FileType getType() {
                return type;
            }

            @Override
            public long getSize() {
                return 1L;
            }

            @Override
            public Map<String, String> getMetadata() {
                return Map.of();
            }
        };
    }
}
