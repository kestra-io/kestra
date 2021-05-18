package io.kestra.storage.local;

import com.google.common.io.CharStreams;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class LocalStorageTest {
    @Inject
    StorageInterface storageInterface;

    private URI putFile(URL resource, String path) throws Exception {
        return storageInterface.put(
            new URI(path),
            new FileInputStream(Objects.requireNonNull(resource).getFile())
        );
    }

    @Test
    void get() throws Exception {
        URL resource = LocalStorageTest.class.getClassLoader().getResource("application.yml");
        String content = CharStreams.toString(new InputStreamReader(new FileInputStream(Objects.requireNonNull(resource).getFile())));

        this.putFile(resource, "/file/storage/get.yml");

        InputStream get = storageInterface.get(new URI("/file/storage/get.yml"));
        assertThat(CharStreams.toString(new InputStreamReader(get)), is(content));

        InputStream getScheme = storageInterface.get(new URI("kestra:///file/storage/get.yml"));
        assertThat(CharStreams.toString(new InputStreamReader(getScheme)), is(content));
    }

    @Test
    void missing() {
        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.get(new URI("/file/storage/missing.yml"));
        });
    }

    @Test
    void put() throws Exception {
        URL resource = LocalStorageTest.class.getClassLoader().getResource("application.yml");
        URI put = this.putFile(resource, "/file/storage/put.yml");
        InputStream get = storageInterface.get(new URI("/file/storage/put.yml"));

        assertThat(put.toString(), is(new URI("kestra:///file/storage/put.yml").toString()));
        assertThat(
            CharStreams.toString(new InputStreamReader(get)),
            is(CharStreams.toString(new InputStreamReader(new FileInputStream(Objects.requireNonNull(resource).getFile()))))
        );

        assertThat(storageInterface.size(new URI("/file/storage/put.yml")), is(77L));

        boolean delete = storageInterface.delete(put);
        assertThat(delete, is(true));

        delete = storageInterface.delete(put);
        assertThat(delete, is(false));

        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.get(new URI("/file/storage/put.yml"));
        });
    }

    @Test
    void deleteByPrefix() throws Exception {
        URL resource = LocalStorageTest.class.getClassLoader().getResource("application.yml");

        List<String> path = Arrays.asList(
            "/file/storage/root.yml",
            "/file/storage/level1/1.yml",
            "/file/storage/level1/level2/1.yml"
        );

        path.forEach(throwConsumer(s -> this.putFile(resource, s)));

        List<URI> deleted = storageInterface.deleteByPrefix(new URI("/file/storage/"));

        assertThat(deleted, containsInAnyOrder(path.stream().map(s -> URI.create("kestra://" + s)).toArray()));

        assertThrows(FileNotFoundException.class, () -> {
            storageInterface.get(new URI("/file/storage/"));
        });

        path
            .forEach(s -> {
                assertThrows(FileNotFoundException.class, () -> {
                    storageInterface.get(new URI(s));
                });
            });
    }
}
