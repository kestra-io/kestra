package io.kestra.core.storage;

import com.google.common.io.CharStreams;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@KestraTest
public abstract class StorageTestSuite {
    private static final String contentString = "Content";

    @Inject
    protected StorageInterface storageInterface;


    //region test GET
    @Test
    void get() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        get(tenantId, prefix);
    }

    @Test
    void getNoTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = null;

        get(tenantId, prefix);
    }

    @Test
    void getNoCrossTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        String withTenant = "/" + prefix + "/storage/withtenant.yml";
        putFile(tenantId, withTenant);
        String nullTenant = "/" + prefix + "/storage/nulltenant.yml";
        putFile(null, nullTenant);

        URI with = new URI(withTenant);
        InputStream get = storageInterface.get(tenantId, with);
        assertThat(CharStreams.toString(new InputStreamReader(get)), is(contentString));
        assertTrue(storageInterface.exists(tenantId, with));
        assertThrows(FileNotFoundException.class, () -> storageInterface.get(null, with));

        URI without = new URI(nullTenant);
        get = storageInterface.get(null, without);
        assertThat(CharStreams.toString(new InputStreamReader(get)), is(contentString));
        assertTrue(storageInterface.exists(null, without));
        assertThrows(FileNotFoundException.class, () -> storageInterface.get(tenantId, without));

    }

    @Test
    void getWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        putFile(tenantId, "/" + prefix + "/storage/get.yml");
        InputStream getScheme = storageInterface.get(tenantId, new URI("kestra:///" + prefix + "/storage/get.yml"));
        assertThat(CharStreams.toString(new InputStreamReader(getScheme)), is(contentString));
    }

    private void get(String tenantId, String prefix) throws Exception {
        putFile(tenantId, "/" + prefix + "/storage/get.yml");
        putFile(tenantId, "/" + prefix + "/storage/level2/2.yml");

        URI item = new URI("/" + prefix + "/storage/get.yml");
        InputStream get = storageInterface.get(tenantId, item);
        assertThat(CharStreams.toString(new InputStreamReader(get)), is(contentString));
        assertTrue(storageInterface.exists(tenantId, item));
    }

    @Test
    void getNoTraversal() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();


        putFile(tenantId, "/" + prefix + "/storage/get.yml");
        putFile(tenantId, "/" + prefix + "/storage/level2/2.yml");
        // Assert that '..' in path cannot be used as gcs do not use directory listing and traversal.
        assertThrows(IllegalArgumentException.class, () -> {
            storageInterface.get(tenantId, new URI("kestra:///" + prefix + "/storage/level2/../get.yml"));
        });
    }

    @Test
    void getFileNotFound() {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.get(tenantId, new URI("/" + prefix + "/storage/missing.yml"));
        });
    }
    //endregion

    @Test
    void filesByPrefix() throws IOException {
        storageInterface.put(null, URI.create("/namespace/file.txt"), new ByteArrayInputStream(new byte[0]));
        storageInterface.put("tenant", URI.create("/namespace/tenant_file.txt"), new ByteArrayInputStream(new byte[0]));
        storageInterface.put(null, URI.create("/namespace/another_file.json"), new ByteArrayInputStream(new byte[0]));
        storageInterface.put(null, URI.create("/namespace/folder/file.txt"), new ByteArrayInputStream(new byte[0]));
        storageInterface.put(null, URI.create("/namespace/folder/some.yaml"), new ByteArrayInputStream(new byte[0]));
        storageInterface.put(null, URI.create("/namespace/folder/sub/script.py"), new ByteArrayInputStream(new byte[0]));

        List<URI> res = storageInterface.allByPrefix(null, URI.create("kestra:///namespace/"), false);
        assertThat(res, containsInAnyOrder(
            URI.create("kestra:///namespace/file.txt"),
            URI.create("kestra:///namespace/another_file.json"),
            URI.create("kestra:///namespace/folder/file.txt"),
            URI.create("kestra:///namespace/folder/some.yaml"),
            URI.create("kestra:///namespace/folder/sub/script.py")
        ));

        res = storageInterface.allByPrefix("tenant", URI.create("/namespace"), false);
        assertThat(res, containsInAnyOrder(URI.create("kestra:///namespace/tenant_file.txt")));

        res = storageInterface.allByPrefix(null, URI.create("/namespace/folder"), false);
        assertThat(res, containsInAnyOrder(
            URI.create("kestra:///namespace/folder/file.txt"),
            URI.create("kestra:///namespace/folder/some.yaml"),
            URI.create("kestra:///namespace/folder/sub/script.py")
        ));

        res = storageInterface.allByPrefix(null, URI.create("/namespace/folder/sub"), false);
        assertThat(res, containsInAnyOrder(URI.create("kestra:///namespace/folder/sub/script.py")));

        res = storageInterface.allByPrefix(null, URI.create("/namespace/non-existing"), false);
        assertThat(res, empty());
    }

    @Test
    void objectsByPrefix() throws IOException {
        storageInterface.put(null, URI.create("/some_namespace/file.txt"), new ByteArrayInputStream(new byte[0]));
        storageInterface.put("tenant", URI.create("/some_namespace/tenant_file.txt"), new ByteArrayInputStream(new byte[0]));
        storageInterface.createDirectory(null, URI.create("/some_namespace/folder/sub"));


        List<URI> res = storageInterface.allByPrefix(null, URI.create("kestra:///some_namespace/"), true);
        assertThat(res, containsInAnyOrder(
            URI.create("kestra:///some_namespace/file.txt"),
            URI.create("kestra:///some_namespace/folder/"),
            URI.create("kestra:///some_namespace/folder/sub/")
        ));

        res = storageInterface.allByPrefix("tenant", URI.create("/some_namespace"), true);
        assertThat(res, containsInAnyOrder(URI.create("kestra:///some_namespace/tenant_file.txt")));

        res = storageInterface.allByPrefix(null, URI.create("/some_namespace/folder"), true);
        assertThat(res, containsInAnyOrder(URI.create("kestra:///some_namespace/folder/sub/")));
    }

    //region test LIST
    @Test
    void list() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        list(prefix, tenantId);
    }

    @Test
    void listNoTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = null;

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
            storageInterface.list(tenantId, new URI("/" + prefix + "/storage/level2/.."));
        });
    }


    @Test
    void listNotFound() {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.list(tenantId, new URI("/" + prefix + "/storage/"));
        });
    }

    @Test
    void listNoCrossTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        List<String> withTenant = Arrays.asList(
            "/" + prefix + "/with/1.yml",
            "/" + prefix + "/with/2.yml",
            "/" + prefix + "/with/3.yml"
        );
        withTenant.forEach(throwConsumer(s -> putFile(tenantId, s)));
        List<String> nullTenant = Arrays.asList(
            "/" + prefix + "/notenant/1.yml",
            "/" + prefix + "/notenant/2.yml",
            "/" + prefix + "/notenant/3.yml"
        );
        nullTenant.forEach(throwConsumer(s -> putFile(null, s)));

        List<FileAttributes> with = storageInterface.list(tenantId, new URI("/" + prefix + "/with"));
        assertThat(with.stream().map(FileAttributes::getFileName).toList(), containsInAnyOrder("1.yml", "2.yml", "3.yml"));
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.list(tenantId, new URI("/" + prefix + "/notenant/"));
        });

        List<FileAttributes> notenant = storageInterface.list(null, new URI("/" + prefix + "/notenant"));
        assertThat(notenant.stream().map(FileAttributes::getFileName).toList(), containsInAnyOrder("1.yml", "2.yml", "3.yml"));
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.list(null, new URI("/" + prefix + "/with/"));
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

        List<FileAttributes> list = storageInterface.list(tenantId, new URI("kestra:///" + prefix + "/storage"));

        assertThat(list.stream().map(FileAttributes::getFileName).toList(), containsInAnyOrder("root.yml", "level1", "another"));
    }

    private void list(String prefix, String tenantId) throws Exception {
        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml",
            "/" + prefix + "/storage/another/1.yml"
        );
        path.forEach(throwConsumer(s -> putFile(tenantId, s)));

        List<FileAttributes> list = storageInterface.list(tenantId, null);
        assertThat(list.stream().map(FileAttributes::getFileName).toList(), hasItem(prefix));

        list = storageInterface.list(tenantId, new URI("/" + prefix + "/storage"));
        assertThat(list.stream().map(FileAttributes::getFileName).toList(), containsInAnyOrder("root.yml", "level1", "another"));
    }
    //endregion

    //region test EXISTS
    @Test
    void exists() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = null;

        exists(prefix, tenantId);
    }

    @Test
    void existsNoTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = null;

        exists(prefix, tenantId);
    }

    private void exists(String prefix, String tenantId) throws Exception {
        putFile(tenantId, "/" + prefix + "/storage/put.yml");
        assertThat(storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/put.yml")), is(true));
        assertThat(storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/notfound.yml")), is(false));
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
            storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/level2/.."));
        });
    }

    @Test
    void existsNoCrossTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        String withTenant = "/" + prefix + "/storage/withtenant.yml";
        putFile(tenantId, withTenant);
        String nullTenant = "/" + prefix + "/storage/nulltenant.yml";
        putFile(null, nullTenant);

        URI with = new URI(withTenant);
        assertTrue(storageInterface.exists(tenantId, with));
        assertFalse(storageInterface.exists(null, with));

        URI without = new URI(nullTenant);
        assertFalse(storageInterface.exists(tenantId, without));
        assertTrue(storageInterface.exists(null, without));

    }

    @Test
    void existsWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        putFile(tenantId, "/" + prefix + "/storage/get.yml");
        assertTrue(storageInterface.exists(tenantId, new URI("kestra:///" + prefix + "/storage/get.yml")));
    }
    //endregion

    //region test SIZE
    @Test
    void size() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = null;

        size(prefix, tenantId);
    }

    @Test
    void sizeNoTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = null;

        size(prefix, tenantId);
    }

    private void size(String prefix, String tenantId) throws Exception {
        URI put = putFile(tenantId, "/" + prefix + "/storage/put.yml");
        assertThat(storageInterface.getAttributes(tenantId, new URI("/" + prefix + "/storage/put.yml")).getSize(), is((long) contentString.length()));
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
            storageInterface.getAttributes(tenantId, new URI("/" + prefix + "/storage/level2/../1.yml")).getSize();
        });
    }

    @Test
    void sizeNotFound() {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(tenantId, new URI("/" + prefix + "/storage/")).getSize();
        });
    }

    @Test
    void sizeNoCrossTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        String withTenant = "/" + prefix + "/storage/withtenant.yml";
        putFile(tenantId, withTenant);
        String nullTenant = "/" + prefix + "/storage/nulltenant.yml";
        putFile(null, nullTenant);

        URI with = new URI(withTenant);
        assertThat(storageInterface.getAttributes(tenantId, with).getSize(), is((long) contentString.length()));
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(null, with).getSize();
        });

        URI without = new URI(nullTenant);
        assertThat(storageInterface.getAttributes(null, without).getSize(), is((long) contentString.length()));
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(tenantId, without).getSize();
        });

    }

    @Test
    void sizeWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        putFile(tenantId, "/" + prefix + "/storage/get.yml");
        assertThat(storageInterface.getAttributes(tenantId, new URI("kestra:///" + prefix + "/storage/get.yml")).getSize(), is((long) contentString.length()));
    }
    //endregion

    //region test LASTMODIFIEDTIME
    @Test
    void lastModifiedTime() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = null;

        lastModifiedTime(prefix, tenantId);
    }

    @Test
    void lastModifiedTimeNoTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = null;

        lastModifiedTime(prefix, tenantId);
    }

    private void lastModifiedTime(String prefix, String tenantId) throws Exception {
        putFile(tenantId, "/" + prefix + "/storage/put.yml");
        assertThat(storageInterface.getAttributes(tenantId, new URI("/" + prefix + "/storage/put.yml")).getLastModifiedTime(), notNullValue());
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
            storageInterface.getAttributes(tenantId, new URI("/" + prefix + "/storage/level2/../1.yml")).getLastModifiedTime();
        });
    }

    @Test
    void lastModifiedTimeNotFound() {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(tenantId, new URI("/" + prefix + "/storage/")).getLastModifiedTime();
        });
    }

    @Test
    void lastModifiedTimeNoCrossTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        String withTenant = "/" + prefix + "/storage/withtenant.yml";
        putFile(tenantId, withTenant);
        String nullTenant = "/" + prefix + "/storage/nulltenant.yml";
        putFile(null, nullTenant);

        URI with = new URI(withTenant);
        assertThat(storageInterface.getAttributes(tenantId, with).getLastModifiedTime(), notNullValue());
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(null, with).getLastModifiedTime();
        });

        URI without = new URI(nullTenant);
        assertThat(storageInterface.getAttributes(null, without).getLastModifiedTime(), notNullValue());
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(tenantId, without).getLastModifiedTime();
        });

    }

    @Test
    void lastModifiedTimeWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        putFile(tenantId, "/" + prefix + "/storage/get.yml");
        assertThat(storageInterface.getAttributes(tenantId, new URI("kestra:///" + prefix + "/storage/get.yml")).getLastModifiedTime(), notNullValue());
    }
    //endregion

    //region test GETATTRIBUTES
    @Test
    void getAttributes() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        getAttributes(prefix, tenantId);
    }

    @Test
    void getAttributesNoTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = null;

        getAttributes(prefix, tenantId);
    }

    private void getAttributes(String prefix, String tenantId) throws Exception {
        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml"
        );
        path.forEach(throwConsumer(s -> this.putFile(tenantId, s)));

        FileAttributes attr = storageInterface.getAttributes(tenantId, new URI("/" + prefix + "/storage/root.yml"));
        assertThat(attr.getFileName(), is("root.yml"));
        assertThat(attr.getType(), is(FileAttributes.FileType.File));
        assertThat(attr.getSize(), is((long) contentString.length()));
        assertThat(attr.getLastModifiedTime(), notNullValue());
        assertThat(attr.getCreationTime(), notNullValue());

        attr = storageInterface.getAttributes(tenantId, new URI("/" + prefix + "/storage/level1"));
        assertThat(attr.getFileName(), is("level1"));
        assertThat(attr.getType(), is(FileAttributes.FileType.Directory));
        assertThat(attr.getLastModifiedTime(), notNullValue());
        assertThat(attr.getCreationTime(), notNullValue());
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
            storageInterface.getAttributes(tenantId, new URI("/" + prefix + "/storage/level2/../1.yml"));
        });
    }

    @Test
    void getAttributesNotFound() {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(tenantId, new URI("/" + prefix + "/storage/"));
        });
    }

    @Test
    void getAttributesNoCrossTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        String withTenant = "/" + prefix + "/storage/withtenant.yml";
        putFile(tenantId, withTenant);
        String nullTenant = "/" + prefix + "/storage/nulltenant.yml";
        putFile(null, nullTenant);

        URI with = new URI(withTenant);
        FileAttributes attr = storageInterface.getAttributes(tenantId, with);
        assertThat(attr.getFileName(), is("withtenant.yml"));
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(null, with);
        });

        URI without = new URI(nullTenant);
        attr = storageInterface.getAttributes(null, without);
        assertThat(attr.getFileName(), is("nulltenant.yml"));
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.getAttributes(tenantId, without);
        });
    }

    @Test
    void getAttributesWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        putFile(tenantId, "/" + prefix + "/storage/get.yml");
        FileAttributes attr = storageInterface.getAttributes(tenantId, new URI("kestra:///" + prefix + "/storage/get.yml"));
        assertThat(attr.getFileName(), is("get.yml"));
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
    void putNoTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = null;

        put(tenantId, prefix);
    }

    @Test
    void putWithScheme() throws URISyntaxException, IOException {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        URI uri = new URI("kestra:///" + prefix + "/storage/get.yml");
        storageInterface.put(
            tenantId,
            uri,
            new ByteArrayInputStream(contentString.getBytes())
        );
        InputStream getScheme = storageInterface.get(tenantId, new URI("/" + prefix + "/storage/get.yml"));
        assertThat(CharStreams.toString(new InputStreamReader(getScheme)), is(contentString));
    }

    @Test
    void putNoTraversal() throws URISyntaxException, IOException {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        storageInterface.createDirectory(tenantId, new URI("/" + prefix + "/storage/level1"));

        assertThrows(IllegalArgumentException.class, () -> {
            storageInterface.put(
                tenantId,
                new URI("kestra:///" + prefix + "/storage/level1/../get2.yml"),
                new ByteArrayInputStream(contentString.getBytes())
            );
        });

    }

    private void put(String tenantId, String prefix) throws Exception {
        URI put = putFile(tenantId, "/" + prefix + "/storage/put.yml");
        InputStream get = storageInterface.get(tenantId, new URI("/" + prefix + "/storage/put.yml"));

        assertThat(put.toString(), is(new URI("kestra:///" + prefix + "/storage/put.yml").toString()));
        assertThat(
            CharStreams.toString(new InputStreamReader(get)),
            is(contentString)
        );
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
    void deleteNoTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = null;

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
            storageInterface.delete(tenantId, new URI("/" + prefix + "/storage/level2/../1.yml"));
        });
    }

    @Test
    void deleteNotFound() throws URISyntaxException, IOException {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        assertThat(storageInterface.delete(tenantId, new URI("/" + prefix + "/storage/")), is(false));
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

        boolean deleted = storageInterface.delete(tenantId, new URI("/" + prefix + "/storage/level1"));
        assertThat(deleted, is(true));
        assertThat(storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/root.yml")), is(true));
        assertThat(storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/another/1.yml")), is(true));
        assertThat(storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/level1")), is(false));
        assertThat(storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/level12.yml")), is(true));
        assertThat(storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/level1/1.yml")), is(false));
        assertThat(storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/level1/level2/1.yml")), is(false));

        deleted = storageInterface.delete(tenantId, new URI("/" + prefix + "/storage/root.yml"));
        assertThat(deleted, is(true));
        assertThat(storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/root.yml")), is(false));

        deleted = storageInterface.delete(tenantId, new URI("/" + prefix + "/storage/file"));
        assertThat(deleted, is(true));
        assertThat(storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/file")), is(false));
        assertThat(storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/file.txt")), is(true));
    }

    @Test
    void deleteWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        putFile(tenantId, "/" + prefix + "/storage/get.yml");
        assertTrue(storageInterface.delete(tenantId, new URI("kestra:///" + prefix + "/storage/get.yml")));
        assertThat(storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/get.yml")), is(false));
    }
    //endregion

    //region test CREATEDIRECTORY
    @Test
    void createDirectory() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = null;

        createDirectory(prefix, tenantId);
    }

    @Test
    void createDirectoryNoTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = null;

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
            storageInterface.createDirectory(tenantId, new URI("/" + prefix + "/storage/level2/../newdir"));
        });
    }

    private void createDirectory(String prefix, String tenantId) throws Exception {
        storageInterface.createDirectory(tenantId, new URI("/" + prefix + "/storage/level1"));
        FileAttributes attr = storageInterface.getAttributes(tenantId, new URI("/" + prefix + "/storage/level1"));
        assertThat(attr.getFileName(), is("level1"));
        assertThat(attr.getType(), is(FileAttributes.FileType.Directory));
        assertThat(attr.getLastModifiedTime(), notNullValue());
    }

    @Test
    void createDirectoryWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        storageInterface.createDirectory(tenantId, new URI("kestra:///" + prefix + "/storage/level1"));
        FileAttributes attr = storageInterface.getAttributes(tenantId, new URI("/" + prefix + "/storage/level1"));
        assertThat(attr.getFileName(), is("level1"));
        assertThat(attr.getType(), is(FileAttributes.FileType.Directory));
        assertThat(attr.getLastModifiedTime(), notNullValue());
    }

    @Test
    void createDirectoryShouldBeRecursive() throws IOException {
        String prefix = IdUtils.create();
        storageInterface.createDirectory(null, URI.create("/" + prefix + "/first/second/third"));

        List<FileAttributes> list = storageInterface.list(null, URI.create("/" + prefix));
        assertThat(list, contains(
            hasProperty("fileName", is("first"))
        ));
    }
    //endregion

    //region test MOVE
    @Test
    void move() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = null;

        move(prefix, tenantId);
    }

    @Test
    void moveNoTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = null;

        move(prefix, tenantId);
    }

    @Test
    void moveNotFound() {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.move(tenantId, new URI("/" + prefix + "/storage/"), new URI("/" + prefix + "/test/"));
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
            storageInterface.move(tenantId, new URI("/" + prefix + "/storage/level2/../1.yml"), new URI("/" + prefix + "/storage/level2/1.yml"));
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

        storageInterface.move(tenantId, new URI("/" + prefix + "/storage/level1"), new URI("/" + prefix + "/storage/moved"));

        List<FileAttributes> list = storageInterface.list(tenantId, new URI("/" + prefix + "/storage/moved"));
        assertThat(storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/level1")), is(false));
        assertThat(list.stream().map(FileAttributes::getFileName).toList(), containsInAnyOrder("level2", "1.yml"));

        list = storageInterface.list(tenantId, new URI("/" + prefix + "/storage/moved/level2"));
        assertThat(list.stream().map(FileAttributes::getFileName).toList(), containsInAnyOrder("2.yml"));

        storageInterface.move(tenantId, new URI("/" + prefix + "/storage/root.yml"), new URI("/" + prefix + "/storage/root-moved.yml"));
        assertThat(storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/root.yml")), is(false));
        assertThat(storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/root-moved.yml")), is(true));
    }

    @Test
    void moveWithScheme() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();

        this.putFile(tenantId, "/" + prefix + "/storage/root.yml");

        storageInterface.move(tenantId, new URI("kestra:///" + prefix + "/storage/root.yml"), new URI("kestra:///" + prefix + "/storage/root-moved.yml"));
        assertThat(storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/root.yml")), is(false));
        assertThat(storageInterface.exists(tenantId, new URI("/" + prefix + "/storage/root-moved.yml")), is(true));
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
    void deleteByPrefixNoTenant() throws Exception {
        String prefix = IdUtils.create();
        String tenantId = null;

        deleteByPrefix(prefix, tenantId);
    }

    @Test
    void deleteByPrefixNotFound() throws URISyntaxException, IOException {
        String prefix = IdUtils.create();
        String tenantId = IdUtils.create();
        assertThat(storageInterface.deleteByPrefix(tenantId, new URI("/" + prefix + "/storage/")), containsInAnyOrder());
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
            storageInterface.move(tenantId, new URI("/" + prefix + "/storage/level2/../1.yml"), new URI("/" + prefix + "/storage/level2/1.yml"));
        });
    }

    private void deleteByPrefix(String prefix, String tenantId) throws Exception {
        List<String> path = Arrays.asList(
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2/1.yml"
        );

        path.forEach(throwConsumer(s -> this.putFile(tenantId, s)));

        List<URI> deleted = storageInterface.deleteByPrefix(tenantId, new URI("/" + prefix + "/storage/"));

        List<String> res = Arrays.asList(
            "/" + prefix + "/storage",
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2",
            "/" + prefix + "/storage/level1/level2/1.yml"
        );

        assertThat(deleted, containsInAnyOrder(res.stream().map(s -> URI.create("kestra://" + s)).toArray()));

        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.get(tenantId, new URI("/" + prefix + "/storage/"));
        });

        path.forEach(throwConsumer(s -> {
            assertThat(storageInterface.exists(tenantId, new URI(s)), is(false));
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

        List<URI> deleted = storageInterface.deleteByPrefix(tenantId, new URI("/" + prefix + "/storage/"));

        List<String> res = Arrays.asList(
            "/" + prefix + "/storage",
            "/" + prefix + "/storage/root.yml",
            "/" + prefix + "/storage/level1",
            "/" + prefix + "/storage/level1/1.yml",
            "/" + prefix + "/storage/level1/level2",
            "/" + prefix + "/storage/level1/level2/1.yml"
        );

        assertThat(deleted, containsInAnyOrder(res.stream().map(s -> URI.create("kestra://" + s)).toArray()));

        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.get(tenantId, new URI("kestra:///" + prefix + "/storage/"));
        });

        path.forEach(throwConsumer(s -> {
            assertThat(storageInterface.exists(tenantId, new URI(s)), is(false));
        }));
    }

    //endregion

    private URI putFile(String tenantId, String path) throws Exception {
        return storageInterface.put(
            tenantId,
            new URI(path),
            new ByteArrayInputStream(contentString.getBytes())
        );
    }
}
