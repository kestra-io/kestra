package io.kestra.core.storage;

import com.google.common.io.CharStreams;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.StorageObject;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.kestra.core.utils.Rethrow.throwConsumer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@KestraTest
public abstract class StorageTestSuite {
    private static final String CONTENT_STRING = "Content";

    @Inject
    protected StorageInterface storageInterface;

    @Test
    void getPath(){
        String path = storageInterface.getPath(null);
        AssertionsForClassTypes.assertThat(path).isEqualTo("");

        path = storageInterface.getPath(URI.create("/folder1/folder2"));
        AssertionsForClassTypes.assertThat(path).isEqualTo("folder1/folder2");

        path = storageInterface.getPath(MAIN_TENANT, null);
        AssertionsForClassTypes.assertThat(path).isEqualTo("main/");

        path = storageInterface.getPath(MAIN_TENANT, URI.create("/folder1/folder2"));
        AssertionsForClassTypes.assertThat(path).isEqualTo("main/folder1/folder2");
    }

    //region test GET
    @Test
    void get() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        get(tenantId, prefix);
    }

    @Test
    void getNoCrossTenant() throws Exception {
        String prefix = IdUtils.create();
        String fistTenant = IdUtils.create();
        String secondTenant = IdUtils.create();

        String fistTenantPath = "/" + prefix + "/storage/firstTenant.yml";
        putFile(fistTenant, fistTenantPath);
        String secondTenantPath = "/" + prefix + "/storage/secondTenant.yml";
        putFile(secondTenant, secondTenantPath);

        URI fistTenantUri = new URI(fistTenantPath);
        InputStream get = storageInterface.get(fistTenant, prefix, fistTenantUri);
        assertThat(CharStreams.toString(new InputStreamReader(get))).isEqualTo(CONTENT_STRING);
        assertTrue(storageInterface.exists(fistTenant, prefix, fistTenantUri));
        assertThrows(FileNotFoundException.class, () -> storageInterface.get(secondTenant, null, fistTenantUri));

        URI secondTenantUri = new URI(secondTenantPath);
        get = storageInterface.get(secondTenant, prefix, secondTenantUri);
        assertThat(CharStreams.toString(new InputStreamReader(get))).isEqualTo(CONTENT_STRING);
        assertTrue(storageInterface.exists(secondTenant, prefix, secondTenantUri));
        assertThrows(FileNotFoundException.class, () -> storageInterface.get(fistTenant, null, secondTenantUri));

    }

    @Test
    void getWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        putFile(tenantId, "/" + prefix + "/storage/get.yml");
        InputStream getScheme = storageInterface.get(tenantId, prefix, new URI("kestra:///" + prefix + "/storage/get.yml"));
        assertThat(CharStreams.toString(new InputStreamReader(getScheme))).isEqualTo(CONTENT_STRING);
    }

    private void get(String tenantId, String prefix) throws Exception {
        putFile(tenantId, "/" + prefix + "/storage/get.yml");
        putFile(tenantId, "/" + prefix + "/storage/level2/2.yml");

        URI item = new URI("/" + prefix + "/storage/get.yml");
        InputStream get = storageInterface.get(tenantId, prefix, item);
        assertThat(CharStreams.toString(new InputStreamReader(get))).isEqualTo(CONTENT_STRING);
        assertTrue(storageInterface.exists(tenantId, prefix, item));
    }

    @Test
    void getNoTraversal() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();


        putFile(tenantId, "/" + prefix + "/storage/get.yml");
        putFile(tenantId, "/" + prefix + "/storage/level2/2.yml");
        // Assert that '..' in path cannot be used as gcs do not use directory listing and traversal.
        assertThrows(IllegalArgumentException.class, () -> {
            storageInterface.get(tenantId, prefix, new URI("kestra:///" + prefix + "/storage/level2/../get.yml"));
        });
    }

    @Test
    void getFileNotFound() {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.get(tenantId, prefix, new URI("/" + prefix + "/storage/missing.yml"));
        });
    }
    //endregion

    @Test
    void getInstanceResource() throws Exception {
        String prefix = IdUtils.create();

        putInstanceFile("/" + prefix + "/storage/get.yml");
        putInstanceFile("/" + prefix + "/storage/level2/2.yml");

        URI item = new URI("/" + prefix + "/storage/get.yml");
        InputStream get = storageInterface.getInstanceResource(prefix, item);
        assertThat(CharStreams.toString(new InputStreamReader(get))).isEqualTo(CONTENT_STRING);
        assertTrue(storageInterface.existsInstanceResource(prefix, item));
    }


    @Test
    void filesByPrefix() throws IOException {
        var namespaceName ="filesByPrefix_test_namespace";
        storageInterface.put(MAIN_TENANT, namespaceName, URI.create("/filesByPrefix_test_namespace/file.txt"), new ByteArrayInputStream(new byte[0]));
        storageInterface.put("tenant", namespaceName, URI.create("/filesByPrefix_test_namespace/tenant_file.txt"), new ByteArrayInputStream(new byte[0]));
        storageInterface.put(MAIN_TENANT, namespaceName, URI.create("/filesByPrefix_test_namespace/another_file.json"), new ByteArrayInputStream(new byte[0]));
        storageInterface.put(MAIN_TENANT, namespaceName, URI.create("/filesByPrefix_test_namespace/folder/file.txt"), new ByteArrayInputStream(new byte[0]));
        storageInterface.put(MAIN_TENANT, namespaceName, URI.create("/filesByPrefix_test_namespace/folder/some.yaml"), new ByteArrayInputStream(new byte[0]));
        storageInterface.put(MAIN_TENANT, namespaceName, URI.create("/filesByPrefix_test_namespace/folder/sub/script.py"), new ByteArrayInputStream(new byte[0]));

        List<URI> res = storageInterface.allByPrefix(MAIN_TENANT, namespaceName, URI.create("kestra:///filesByPrefix_test_namespace/"), false);
        assertThat(res).containsExactlyInAnyOrder(URI.create("kestra:///filesByPrefix_test_namespace/file.txt"), URI.create("kestra:///filesByPrefix_test_namespace/another_file.json"), URI.create("kestra:///filesByPrefix_test_namespace/folder/file.txt"), URI.create("kestra:///filesByPrefix_test_namespace/folder/some.yaml"), URI.create("kestra:///filesByPrefix_test_namespace/folder/sub/script.py"));

        res = storageInterface.allByPrefix("tenant", namespaceName, URI.create("/filesByPrefix_test_namespace"), false);
        assertThat(res).containsExactlyInAnyOrder(URI.create("kestra:///filesByPrefix_test_namespace/tenant_file.txt"));

        res = storageInterface.allByPrefix(MAIN_TENANT, namespaceName, URI.create("/filesByPrefix_test_namespace/folder"), false);
        assertThat(res).containsExactlyInAnyOrder(URI.create("kestra:///filesByPrefix_test_namespace/folder/file.txt"), URI.create("kestra:///filesByPrefix_test_namespace/folder/some.yaml"), URI.create("kestra:///filesByPrefix_test_namespace/folder/sub/script.py"));

        res = storageInterface.allByPrefix(MAIN_TENANT, namespaceName, URI.create("/filesByPrefix_test_namespace/folder/sub"), false);
        assertThat(res).containsExactlyInAnyOrder(URI.create("kestra:///filesByPrefix_test_namespace/folder/sub/script.py"));

        res = storageInterface.allByPrefix(MAIN_TENANT, namespaceName, URI.create("/filesByPrefix_test_namespace/non-existing"), false);
        assertThat(res).isEmpty();
    }

    @Test
    void objectsByPrefix() throws IOException {
        storageInterface.put(MAIN_TENANT, "some_namespace", URI.create("/some_namespace/file.txt"), new ByteArrayInputStream(new byte[0]));
        storageInterface.put("tenant", "some_namespace", URI.create("/some_namespace/tenant_file.txt"), new ByteArrayInputStream(new byte[0]));
        storageInterface.createDirectory(MAIN_TENANT, "some_namespace", URI.create("/some_namespace/folder/sub"));


        List<URI> res = storageInterface.allByPrefix(MAIN_TENANT, "some_namespace", URI.create("kestra:///some_namespace/"), true);
        assertThat(res).containsExactlyInAnyOrder(URI.create("kestra:///some_namespace/file.txt"), URI.create("kestra:///some_namespace/folder/"), URI.create("kestra:///some_namespace/folder/sub/"));

        res = storageInterface.allByPrefix("tenant", "some_namespace", URI.create("/some_namespace"), true);
        assertThat(res).containsExactlyInAnyOrder(URI.create("kestra:///some_namespace/tenant_file.txt"));

        res = storageInterface.allByPrefix(MAIN_TENANT, "some_namespace", URI.create("/some_namespace/folder"), true);
        assertThat(res).containsExactlyInAnyOrder(URI.create("kestra:///some_namespace/folder/sub/"));
    }

    //region test LIST
    @Test
    void list() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        list(prefix, tenantId);
    }

    @Test
    void listNoTraversal() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml",
            "/" + prefix + "/storage/another/1.yml"
        );
        path.forEach(throwConsumer(s -> putFile(tenantId, s)));

        assertThrows(IllegalArgumentException.class, () -> {
            storageInterface.list(tenantId, prefix, new URI("/" + prefix + "/storage/level2/.."));
        });
    }


    @Test
    void listNotFound() {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.list(tenantId, prefix, new URI("/" + prefix + "/storage/"));
        });
    }

    @Test
    void listNoCrossTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId1 = IdUtils.create();
        String tenantId2 = IdUtils.create();

        List<String> firstTenant = Arrays.asList(
            "/" + prefix + "/with/1.yml",
            "/" + prefix + "/with/2.yml",
            "/" + prefix + "/with/3.yml"
        );
        firstTenant.forEach(throwConsumer(s -> putFile(tenantId1, s)));
        List<String> secondTenant = Arrays.asList(
            "/" + prefix + "/notenant/1.yml",
            "/" + prefix + "/notenant/2.yml",
            "/" + prefix + "/notenant/3.yml"
        );
        secondTenant.forEach(throwConsumer(s -> putFile(tenantId2, s)));

        List<FileAttributes> with = storageInterface.list(tenantId1, prefix, new URI("/" + prefix + "/with"));
        assertThat(with.stream().map(FileAttributes::getFileName).toList()).containsExactlyInAnyOrder("1.yml", "2.yml", "3.yml");
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.list(tenantId1, prefix, new URI("/" + prefix + "/notenant/"));
        });

        List<FileAttributes> notenant = storageInterface.list(tenantId2, prefix, new URI("/" + prefix + "/notenant"));
        assertThat(notenant.stream().map(FileAttributes::getFileName).toList()).containsExactlyInAnyOrder("1.yml", "2.yml", "3.yml");
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.list(tenantId2, prefix, new URI("/" + prefix + "/with/"));
        });
    }

    @Test
    void listWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml",
            "/" + prefix + "/storage/another/1.yml"
        );
        path.forEach(throwConsumer(s -> putFile(tenantId, s)));

        List<FileAttributes> list = storageInterface.list(tenantId, prefix, new URI("kestra:///" + prefix + "/storage"));

        assertThat(list.stream().map(FileAttributes::getFileName).toList()).containsExactlyInAnyOrder("root.yml", "level1", "another");
    }

    private void list(String prefix, String tenantId) throws Exception {
        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml",
            "/" + prefix + "/storage/another/1.yml"
        );
        path.forEach(throwConsumer(s -> putFile(tenantId, s, Map.of("someMetadata", "someValue"))));

        List<FileAttributes> list = storageInterface.list(tenantId, prefix, null);
        assertThat(list.stream().map(FileAttributes::getFileName).toList()).contains(prefix);

        list = storageInterface.list(tenantId, prefix, new URI("/" + prefix + "/storage"));
        assertThat(list.stream().map(FileAttributes::getFileName).toList()).containsExactlyInAnyOrder("root.yml", "level1", "another");
        assertThat(list.stream().filter(f -> f.getFileName().equals("root.yml")).findFirst().get().getMetadata()).containsEntry("someMetadata", "someValue");
    }
    //endregion

    @Test
    void listInstanceResouces() throws Exception {
        String prefix = IdUtils.create();

        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml",
            "/" + prefix + "/storage/another/1.yml"
        );
        path.forEach(throwConsumer(s -> putInstanceFile(s, Map.of("someMetadata", "someValue"))));

        List<FileAttributes> list = storageInterface.listInstanceResource(prefix, null);
        assertThat(list.stream().map(FileAttributes::getFileName).toList()).contains(prefix);

        list = storageInterface.listInstanceResource(prefix, new URI("/" + prefix + "/storage"));
        assertThat(list.stream().map(FileAttributes::getFileName).toList()).containsExactlyInAnyOrder("root.yml", "level1", "another");
        assertThat(list.stream().filter(f -> f.getFileName().equals("root.yml")).findFirst().get().getMetadata()).containsEntry("someMetadata", "someValue");
    }


    //region test EXISTS
    @Test
    void exists() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        exists(prefix, tenantId);
    }

    private void exists(String prefix, String tenantId) throws Exception {
        putFile(tenantId, "/" + prefix + "/storage/put.yml");
        assertThat(storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/put.yml"))).isTrue();
        assertThat(storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/notfound.yml"))).isFalse();
    }

    @Test
    void existsNoTraversal() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml",
            "/" + prefix + "/storage/another/1.yml"
        );
        path.forEach(throwConsumer(s -> putFile(tenantId, s)));

        assertThrows(IllegalArgumentException.class, () -> {
            storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/level2/.."));
        });
    }

    @Test
    void existsNoCrossTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId1 = IdUtils.create();
        String tenantId2 = IdUtils.create();

        String firstTenant = "/" + prefix + "/storage/firstTenant.yml";
        putFile(tenantId1, firstTenant);
        String secondTenant = "/" + prefix + "/storage/secondTenant.yml";
        putFile(tenantId2, secondTenant);

        URI with = new URI(firstTenant);
        assertTrue(storageInterface.exists(tenantId1, prefix, with));
        assertFalse(storageInterface.exists(tenantId2, prefix, with));

        URI without = new URI(secondTenant);
        assertFalse(storageInterface.exists(tenantId1, prefix, without));
        assertTrue(storageInterface.exists(tenantId2, prefix, without));

    }

    @Test
    void existsWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        putFile(tenantId, "/" + prefix + "/storage/get.yml");
        assertTrue(storageInterface.exists(tenantId, prefix, new URI("kestra:///" + prefix + "/storage/get.yml")));
    }

    @Test
    void existsInstanceResource() throws Exception {
        String prefix = IdUtils.create();

        putInstanceFile("/" + prefix + "/storage/put.yml");
        assertThat(storageInterface.existsInstanceResource(prefix, new URI("/" + prefix + "/storage/put.yml"))).isTrue();
        assertThat(storageInterface.existsInstanceResource(prefix, new URI("/" + prefix + "/storage/notfound.yml"))).isFalse();
    }
    //endregion

    //region test SIZE
    @Test
    void size() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        size(prefix, tenantId);
    }

    private void size(String prefix, String tenantId) throws Exception {
        URI put = putFile(tenantId, "/" + prefix + "/storage/put.yml");
        assertThat(storageInterface.getAttributes(tenantId, prefix, new URI("/" + prefix + "/storage/put.yml")).getSize()).isEqualTo((long) CONTENT_STRING.length());
    }

    @Test
    void sizeNoTraversal() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml"
        );
        path.forEach(throwConsumer(s -> putFile(tenantId, s)));

        assertThrows(IllegalArgumentException.class, () -> {
            storageInterface.getAttributes(tenantId, prefix, new URI("/" + prefix + "/storage/level2/../1.yml")).getSize();
        });
    }

    @Test
    void sizeNotFound() {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(tenantId, prefix, new URI("/" + prefix + "/storage/")).getSize();
        });
    }

    @Test
    void sizeNoCrossTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId1 = IdUtils.create();
        String tenantId2 = IdUtils.create();

        String firstTenant = "/" + prefix + "/storage/firstTenant.yml";
        putFile(tenantId1, firstTenant);
        String secondTenant = "/" + prefix + "/storage/secondTenant.yml";
        putFile(tenantId2, secondTenant);

        URI with = new URI(firstTenant);
        assertThat(storageInterface.getAttributes(tenantId1, prefix, with).getSize()).isEqualTo((long) CONTENT_STRING.length());
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(tenantId2, prefix, with).getSize();
        });

        URI without = new URI(secondTenant);
        assertThat(storageInterface.getAttributes(tenantId2, prefix, without).getSize()).isEqualTo((long) CONTENT_STRING.length());
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(tenantId1, prefix, without).getSize();
        });

    }

    @Test
    void sizeWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        putFile(tenantId, "/" + prefix + "/storage/get.yml");
        assertThat(storageInterface.getAttributes(tenantId, prefix, new URI("kestra:///" + prefix + "/storage/get.yml")).getSize()).isEqualTo((long) CONTENT_STRING.length());
    }
    //endregion

    //region test LASTMODIFIEDTIME
    @Test
    void lastModifiedTime() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        lastModifiedTime(prefix, tenantId);
    }

    private void lastModifiedTime(String prefix, String tenantId) throws Exception {
        putFile(tenantId, "/" + prefix + "/storage/put.yml");
        assertThat(storageInterface.getAttributes(tenantId, prefix, new URI("/" + prefix + "/storage/put.yml")).getLastModifiedTime()).isNotNull();
    }

    @Test
    void lastModifiedTimeNoTraversal() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml"
        );
        path.forEach(throwConsumer(s -> putFile(tenantId, s)));

        assertThrows(IllegalArgumentException.class, () -> {
            storageInterface.getAttributes(tenantId, prefix, new URI("/" + prefix + "/storage/level2/../1.yml")).getLastModifiedTime();
        });
    }

    @Test
    void lastModifiedTimeNotFound() {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(tenantId, prefix, new URI("/" + prefix + "/storage/")).getLastModifiedTime();
        });
    }

    @Test
    void lastModifiedTimeNoCrossTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId1 = IdUtils.create();
        String tenantId2 = IdUtils.create();

        String firstTenant = "/" + prefix + "/storage/firstTenant.yml";
        putFile(tenantId1, firstTenant);
        String secondTenant = "/" + prefix + "/storage/secondTenant.yml";
        putFile(tenantId2, secondTenant);

        URI with = new URI(firstTenant);
        assertThat(storageInterface.getAttributes(tenantId1, prefix, with).getLastModifiedTime()).isNotNull();
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(tenantId2, prefix, with).getLastModifiedTime();
        });

        URI without = new URI(secondTenant);
        assertThat(storageInterface.getAttributes(tenantId2, prefix, without).getLastModifiedTime()).isNotNull();
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(tenantId1, prefix, without).getLastModifiedTime();
        });

    }

    @Test
    void lastModifiedTimeWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        putFile(tenantId, "/" + prefix + "/storage/get.yml");
        assertThat(storageInterface.getAttributes(tenantId, prefix, new URI("kestra:///" + prefix + "/storage/get.yml")).getLastModifiedTime()).isNotNull();
    }
    //endregion

    //region test GETATTRIBUTES
    @Test
    void getAttributes() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        getAttributes(prefix, tenantId);
    }

    private void getAttributes(String prefix, String tenantId) throws Exception {
        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml"
        );
        path.forEach(throwConsumer(s -> this.putFile(tenantId, s)));

        FileAttributes attr = storageInterface.getAttributes(tenantId, prefix, new URI("/" + prefix + "/storage/root.yml"));
        compareFileAttribute(attr);

        attr = storageInterface.getAttributes(tenantId, prefix, new URI("/" + prefix + "/storage/level1"));
        compareDirectory(attr);
    }

    @Test
    void getAttributesNoTraversal() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml"
        );
        path.forEach(throwConsumer(s -> putFile(tenantId, s)));

        assertThrows(IllegalArgumentException.class, () -> {
            storageInterface.getAttributes(tenantId, prefix, new URI("/" + prefix + "/storage/level2/../1.yml"));
        });
    }

    @Test
    void getAttributesNotFound() {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(tenantId, prefix, new URI("/" + prefix + "/storage/"));
        });
    }

    @Test
    void getAttributesNoCrossTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId1 = IdUtils.create();
        String tenantId2 = IdUtils.create();

        String firstTenant = "/" + prefix + "/storage/firstTenant.yml";
        putFile(tenantId1, firstTenant);
        String secondTenant = "/" + prefix + "/storage/secondTenant.yml";
        putFile(tenantId2, secondTenant);

        URI with = new URI(firstTenant);
        FileAttributes attr = storageInterface.getAttributes(tenantId1, prefix, with);
        assertThat(attr.getFileName()).isEqualTo("firstTenant.yml");
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(tenantId2, prefix, with);
        });

        URI without = new URI(secondTenant);
        attr = storageInterface.getAttributes(tenantId2, prefix, without);
        assertThat(attr.getFileName()).isEqualTo("secondTenant.yml");
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(tenantId1, prefix, without);
        });
    }

    @Test
    void getAttributesWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        putFile(tenantId, "/" + prefix + "/storage/get.yml");
        FileAttributes attr = storageInterface.getAttributes(tenantId, prefix, new URI("kestra:///" + prefix + "/storage/get.yml"));
        assertThat(attr.getFileName()).isEqualTo("get.yml");
    }

    @Test
    void getInstanceAttributes() throws Exception {
        String prefix = IdUtils.create();

        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml"
        );
        path.forEach(throwConsumer(this::putInstanceFile));

        FileAttributes attr = storageInterface.getInstanceAttributes(prefix, new URI("/" + prefix + "/storage/root.yml"));
        compareFileAttribute(attr);

        attr = storageInterface.getInstanceAttributes(prefix, new URI("/" + prefix + "/storage/level1"));
        compareDirectory(attr);
    }

    private static void compareDirectory(FileAttributes attr) {
        assertThat(attr.getFileName()).isEqualTo("level1");
        assertThat(attr.getType()).isEqualTo(FileAttributes.FileType.Directory);
        Instant lastModifiedInstant = Instant.ofEpochMilli(attr.getLastModifiedTime());
        assertThat(lastModifiedInstant).isCloseTo(Instant.now(), within(Duration.ofSeconds(10)));
        Instant creationInstant = Instant.ofEpochMilli(attr.getCreationTime());
        assertThat(creationInstant).isCloseTo(Instant.now(), within(Duration.ofSeconds(10)));
    }

    private static void compareFileAttribute(FileAttributes attr) {
        assertThat(attr.getFileName()).isEqualTo("root.yml");
        assertThat(attr.getType()).isEqualTo(FileAttributes.FileType.File);
        assertThat(attr.getSize()).isEqualTo((long) CONTENT_STRING.length());
        Instant lastModifiedInstant = Instant.ofEpochMilli(attr.getLastModifiedTime());
        assertThat(lastModifiedInstant).isCloseTo(Instant.now(), within(Duration.ofSeconds(10)));
        Instant creationInstant = Instant.ofEpochMilli(attr.getCreationTime());
        assertThat(creationInstant).isCloseTo(Instant.now(), within(Duration.ofSeconds(10)));
    }
    //endregion

    //region test PUT
    @Test
    void put() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        put(tenantId, prefix);
    }

    @Test
    void put_PathWithTenantStringInIt() throws Exception {
        String tenantId = IdUtils.create();
        String prefix = tenantId + "/" + IdUtils.create();

        put(tenantId, prefix);
    }

    @Test
    void putFromAnotherFile() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        put(tenantId, prefix);

        URI putFromAnother = storageInterface.put(
            tenantId,
            prefix,
            new URI("/" + prefix + "/storage/put_from_another.yml"),
            storageInterface.get(tenantId, prefix, new URI("/" + prefix + "/storage/put.yml"))
        );

        assertThat(putFromAnother.toString()).isEqualTo(new URI("kestra:///" + prefix + "/storage/put_from_another.yml").toString());
        InputStream get = storageInterface.get(tenantId, prefix, new URI("/" + prefix + "/storage/put_from_another.yml"));
        assertThat(CharStreams.toString(new InputStreamReader(get))).isEqualTo(CONTENT_STRING);
    }

    @Test
    void putWithScheme() throws URISyntaxException, IOException {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        URI uri = new URI("kestra:///" + prefix + "/storage/get.yml");
        storageInterface.put(
            tenantId,
            prefix,
            uri,
            new ByteArrayInputStream(CONTENT_STRING.getBytes())
        );
        InputStream getScheme = storageInterface.get(tenantId, prefix, new URI("/" + prefix + "/storage/get.yml"));
        assertThat(CharStreams.toString(new InputStreamReader(getScheme))).isEqualTo(CONTENT_STRING);
    }

    @Test
    void putNoTraversal() throws URISyntaxException, IOException {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        storageInterface.createDirectory(tenantId, prefix, new URI("/" + prefix + "/storage/level1"));

        assertThrows(IllegalArgumentException.class, () -> {
            storageInterface.put(
                tenantId,
                prefix,
                new URI("kestra:///" + prefix + "/storage/level1/../get2.yml"),
                new ByteArrayInputStream(CONTENT_STRING.getBytes())
            );
        });

    }

    private void put(String tenantId, String prefix) throws Exception {
        URI put = putFile(tenantId, "/" + prefix + "/storage/put.yml");
        InputStream get = storageInterface.get(tenantId, prefix, new URI("/" + prefix + "/storage/put.yml"));

        assertThat(put.toString()).isEqualTo(new URI("kestra:///" + prefix + "/storage/put.yml").toString());
        assertThat(CharStreams.toString(new InputStreamReader(get))).isEqualTo(CONTENT_STRING);
    }

    @Test
    void putInstanceResource() throws Exception {
        String prefix = IdUtils.create();

        URI put = putInstanceFile("/" + prefix + "/storage/put.yml");
        InputStream get = storageInterface.getInstanceResource(prefix, new URI("/" + prefix + "/storage/put.yml"));

        assertThat(put.toString()).isEqualTo(new URI("kestra:///" + prefix + "/storage/put.yml").toString());
        assertThat(CharStreams.toString(new InputStreamReader(get))).isEqualTo(CONTENT_STRING);
    }
    //endregion

    //region test DELETE
    @Test
    void delete() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        delete(prefix, tenantId);
    }

    @Test
    void deleteNoTraversal() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml",
            "/" + prefix + "/storage/another/1.yml"
        );
        path.forEach(throwConsumer(s -> this.putFile(tenantId, s)));

        assertThrows(IllegalArgumentException.class, () -> {
            storageInterface.delete(tenantId, prefix, new URI("/" + prefix + "/storage/level2/../1.yml"));
        });
    }

    @Test
    void deleteNotFound() throws URISyntaxException, IOException {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        assertThat(storageInterface.delete(tenantId, prefix, new URI("/" + prefix + "/storage/"))).isFalse();
    }

    private void delete(String prefix, String tenantId) throws Exception {
        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level12.yml",
            "/" + prefix + "/storage/file",
            "/" + prefix + "/storage/file.txt",
            "/" + prefix + "/storage/level1/level2/1.yml",
            "/" + prefix + "/storage/another/1.yml"
        );
        path.forEach(throwConsumer(s -> this.putFile(tenantId, s)));

        boolean deleted = storageInterface.delete(tenantId, prefix, new URI("/" + prefix + "/storage/level1"));
        assertThat(deleted).isTrue();
        assertThat(storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/root.yml"))).isTrue();
        assertThat(storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/another/1.yml"))).isTrue();
        assertThat(storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/level1"))).isFalse();
        assertThat(storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/level12.yml"))).isTrue();
        assertThat(storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/level1/1.yml"))).isFalse();
        assertThat(storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/level1/level2/1.yml"))).isFalse();

        deleted = storageInterface.delete(tenantId, prefix, new URI("/" + prefix + "/storage/root.yml"));
        assertThat(deleted).isTrue();
        assertThat(storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/root.yml"))).isFalse();

        deleted = storageInterface.delete(tenantId, prefix, new URI("/" + prefix + "/storage/file"));
        assertThat(deleted).isTrue();
        assertThat(storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/file"))).isFalse();
        assertThat(storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/file.txt"))).isTrue();
    }

    @Test
    void deleteWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        putFile(tenantId, "/" + prefix + "/storage/get.yml");
        assertTrue(storageInterface.delete(tenantId, prefix, new URI("kestra:///" + prefix + "/storage/get.yml")));
        assertThat(storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/get.yml"))).isFalse();
    }

    @Test
    void deleteInstanceResource() throws Exception {
        String prefix = IdUtils.create();

        List<String> paths = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml"
        );
        paths.forEach(throwConsumer(this::putInstanceFile));

        assertThat(storageInterface.existsInstanceResource(prefix, new URI("/" + prefix + "/storage/root.yml"))).isTrue();
        assertThat(storageInterface.existsInstanceResource(prefix, new URI("/" + prefix + "/storage/level1/1.yml"))).isTrue();
        assertThat(storageInterface.existsInstanceResource(prefix, new URI("/" + prefix + "/storage/level1/level2/1.yml"))).isTrue();

        boolean deleted = storageInterface.deleteInstanceResource(prefix, new URI("/" + prefix + "/storage/level1"));
        assertThat(deleted).isTrue();
        assertThat(storageInterface.existsInstanceResource(prefix, new URI("/" + prefix + "/storage/root.yml"))).isTrue();
        assertThat(storageInterface.existsInstanceResource(prefix, new URI("/" + prefix + "/storage/level1/1.yml"))).isFalse();
        assertThat(storageInterface.existsInstanceResource(prefix, new URI("/" + prefix + "/storage/level1/level2/1.yml"))).isFalse();
    }
    //endregion

    //region test CREATEDIRECTORY
    @Test
    void createDirectory() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        createDirectory(prefix, tenantId);
    }

    @Test
    void createDirectoryNoTraversal() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml",
            "/" + prefix + "/storage/another/1.yml"
        );
        path.forEach(throwConsumer(s -> this.putFile(tenantId, s)));

        assertThrows(IllegalArgumentException.class, () -> {
            storageInterface.createDirectory(tenantId, prefix, new URI("/" + prefix + "/storage/level2/../newdir"));
        });
    }

    private void createDirectory(String prefix, String tenantId) throws Exception {
        storageInterface.createDirectory(tenantId, prefix, new URI("/" + prefix + "/storage/level1"));
        FileAttributes attr = storageInterface.getAttributes(tenantId, prefix, new URI("/" + prefix + "/storage/level1"));
        assertThat(attr.getFileName()).isEqualTo("level1");
        assertThat(attr.getType()).isEqualTo(FileAttributes.FileType.Directory);
        assertThat(attr.getLastModifiedTime()).isNotNull();
    }

    @Test
    void createDirectoryWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        storageInterface.createDirectory(tenantId, prefix, new URI("kestra:///" + prefix + "/storage/level1"));
        FileAttributes attr = storageInterface.getAttributes(tenantId, prefix, new URI("/" + prefix + "/storage/level1"));
        assertThat(attr.getFileName()).isEqualTo("level1");
        assertThat(attr.getType()).isEqualTo(FileAttributes.FileType.Directory);
        assertThat(attr.getLastModifiedTime()).isNotNull();
    }

    @Test
    void createDirectoryShouldBeRecursive() throws IOException {
        String prefix = IdUtils.create();
        storageInterface.createDirectory(MAIN_TENANT, prefix, URI.create("/" + prefix + "/first/second/third"));

        List<FileAttributes> list = storageInterface.list(MAIN_TENANT, prefix, URI.create("/" + prefix));
        assertThat(list, contains(
            hasProperty("fileName", is("first"))
        ));
    }

    @Test
    void createInstanceDirectory() throws Exception {
        String prefix = IdUtils.create();

        storageInterface.createInstanceDirectory(prefix, new URI("/" + prefix + "/storage/level1"));
        FileAttributes attr = storageInterface.getInstanceAttributes(prefix, new URI("/" + prefix + "/storage/level1"));
        assertThat(attr.getFileName()).isEqualTo("level1");
        assertThat(attr.getType()).isEqualTo(FileAttributes.FileType.Directory);
        assertThat(attr.getLastModifiedTime()).isNotNull();
    }
    //endregion

    //region test MOVE
    @Test
    void move() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        move(prefix, tenantId);
    }

    @Test
    void moveNotFound() {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.move(tenantId, prefix, new URI("/" + prefix + "/storage/"), new URI("/" + prefix + "/test/"));
        });
    }

    @Test
    void moveNoTraversal() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml",
            "/" + prefix + "/storage/another/1.yml"
        );
        path.forEach(throwConsumer(s -> this.putFile(tenantId, s)));

        assertThrows(IllegalArgumentException.class, () -> {
            storageInterface.move(tenantId, prefix, new URI("/" + prefix + "/storage/level2/../1.yml"), new URI("/" + prefix + "/storage/level2/1.yml"));
        });
    }

    private void move(String prefix, String tenantId) throws Exception {
        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/2.yml",
            "/" + prefix + "/storage/another/1.yml"
        );
        path.forEach(throwConsumer(s -> this.putFile(tenantId, s)));

        storageInterface.move(tenantId, prefix, new URI("/" + prefix + "/storage/level1"), new URI("/" + prefix + "/storage/moved"));

        List<FileAttributes> list = storageInterface.list(tenantId, prefix, new URI("/" + prefix + "/storage/moved"));
        assertThat(storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/level1"))).isFalse();
        assertThat(list.stream().map(FileAttributes::getFileName).toList()).containsExactlyInAnyOrder("level2", "1.yml");

        list = storageInterface.list(tenantId, prefix, new URI("/" + prefix + "/storage/moved/level2"));
        assertThat(list.stream().map(FileAttributes::getFileName).toList()).containsExactlyInAnyOrder("2.yml");

        storageInterface.move(tenantId, prefix, new URI("/" + prefix + "/storage/root.yml"), new URI("/" + prefix + "/storage/root-moved.yml"));
        assertThat(storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/root.yml"))).isFalse();
        assertThat(storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/root-moved.yml"))).isTrue();
    }

    @Test
    void moveWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        this.putFile(tenantId, "/" + prefix + "/storage/root.yml");

        storageInterface.move(tenantId, prefix, new URI("kestra:///" + prefix + "/storage/root.yml"), new URI("kestra:///" + prefix + "/storage/root-moved.yml"));
        assertThat(storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/root.yml"))).isFalse();
        assertThat(storageInterface.exists(tenantId, prefix, new URI("/" + prefix + "/storage/root-moved.yml"))).isTrue();
    }
    //endregion

    //region test DELETEBYPREFIX
    @Test
    void deleteByPrefix() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        deleteByPrefix(prefix, tenantId);
    }

    @Test
    void deleteByPrefix_PathWithTenantStringInIt() throws Exception {
        String tenantId = IdUtils.create();
        String prefix = tenantId + "/" + IdUtils.create();

        deleteByPrefix(prefix, tenantId);
    }

    @Test
    void deleteByPrefixNotFound() throws URISyntaxException, IOException {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        assertThat(storageInterface.deleteByPrefix(tenantId, prefix, new URI("/" + prefix + "/storage/"))).containsExactlyInAnyOrder();
    }

    @Test
    void deleteByPrefixNoTraversal() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml",
            "/" + prefix + "/storage/another/1.yml"
        );
        path.forEach(throwConsumer(s -> this.putFile(tenantId, s)));

        assertThrows(IllegalArgumentException.class, () -> {
            storageInterface.move(tenantId, prefix, new URI("/" + prefix + "/storage/level2/../1.yml"), new URI("/" + prefix + "/storage/level2/1.yml"));
        });
    }

    private void deleteByPrefix(String prefix, String tenantId) throws Exception {
        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml"
        );

        path.forEach(throwConsumer(s -> this.putFile(tenantId, s)));

        List<URI> deleted = storageInterface.deleteByPrefix(tenantId, prefix, new URI("/" + prefix + "/storage/"));

        List<String> res = Arrays.asList(
            "/" + prefix + "/storage",
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2",
            "/" + prefix + "/storage/level1/level2/1.yml"
        );

        assertThat(deleted).containsExactlyInAnyOrder(res.stream().map(s -> URI.create("kestra://" + s)).toArray(URI[]::new));

        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.get(tenantId, prefix, new URI("/" + prefix + "/storage/"));
        });

        path.forEach(throwConsumer(s -> {
            assertThat(storageInterface.exists(tenantId, prefix, new URI(s))).isFalse();
        }));
    }

    @Test
    void deleteByPrefixWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml"
        );

        path.forEach(throwConsumer(s -> this.putFile(tenantId, s)));

        List<URI> deleted = storageInterface.deleteByPrefix(tenantId, prefix, new URI("/" + prefix + "/storage/"));

        List<String> res = Arrays.asList(
            "/" + prefix + "/storage",
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2",
            "/" + prefix + "/storage/level1/level2/1.yml"
        );

        assertThat(deleted).containsExactlyInAnyOrder(res.stream().map(s -> URI.create("kestra://" + s)).toArray(URI[]::new));

        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.get(tenantId, prefix, new URI("kestra:///" + prefix + "/storage/"));
        });

        path.forEach(throwConsumer(s -> {
            assertThat(storageInterface.exists(tenantId, prefix, new URI(s))).isFalse();
        }));
    }
    //endregion

    @Test
    void metadata() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        Map<String, String> expectedMetadata = Map.of(
            "someComplexKey1", "value1",
            "anotherComplexKey2", "value2"
        );
        putFile(tenantId, "/" + prefix + "/storage/get.yml", expectedMetadata);
        StorageObject withMetadata = storageInterface.getWithMetadata(tenantId, null, new URI("kestra:///" + prefix + "/storage/get.yml"));
        assertThat(CharStreams.toString(new InputStreamReader(withMetadata.inputStream()))).isEqualTo(CONTENT_STRING);
        assertThat(withMetadata.metadata()).isEqualTo(expectedMetadata);
    }

    @Test
    void limitShouldPreserveSpecialCharts() throws IOException {
        var uri = URI.create("/%89%B4%89%B4%EC%9D%B4%EC%96%B4+%EB%A7%90+%EC%95%84%ED%8A%B8%EC%9B%8D+NP+%EC%8A%A4%ED%8C%90+%EC%9D%B8%ED%8C%85+JQ+%EB%82%A8%EC%84%B1+%EC%9D%B8%EB%B0%B4%EB%93%9C+%EB%93%9C%EB%A1%9C%EC%A6%88%2C+101470%2C+FI261DR15M001-21-1st+Fit+%28QC%29+Sample+Data+Package-en.txt");

        var limited = storageInterface.limit(uri, 100);
        assertThat(uri.getPath()).endsWith(limited.getPath().substring(7));
    }

    private URI putFile(String tenantId, String path) throws Exception {
        return storageInterface.put(
            tenantId,
            null,
            new URI(path),
            new ByteArrayInputStream(CONTENT_STRING.getBytes())
        );
    }

    private URI putFile(String tenantId, String path, Map<String, String> metadata) throws Exception {
        return storageInterface.put(
            tenantId,
            null,
            new URI(path),
            new StorageObject(
                metadata,
                new ByteArrayInputStream(CONTENT_STRING.getBytes())
            )
        );
    }

    private URI putInstanceFile(String path) throws Exception {
        return storageInterface.putInstanceResource(
            null,
            new URI(path),
            new ByteArrayInputStream(CONTENT_STRING.getBytes())
        );
    }

    private URI putInstanceFile(String path, Map<String, String> metadata) throws Exception {
        return storageInterface.putInstanceResource(
            null,
            new URI(path),
            new StorageObject(
                metadata,
                new ByteArrayInputStream(CONTENT_STRING.getBytes())
            )
        );
    }
}
