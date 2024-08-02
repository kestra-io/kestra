package io.kestra.core.storages;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStoreValueWrapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.storage.local.LocalStorage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class InternalKVStoreTest {
    private static final Instant date = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final Map<String, Object> complexValue = Map.of("some", "complex", "object", Map.of("with", "nested", "values", date));
    private static final Logger logger = LoggerFactory.getLogger(InternalKVStoreTest.class);

    LocalStorage storageInterface;

    @BeforeEach
    public void setUp() throws IOException {
        Path basePath = Files.createTempDirectory("unit");
        storageInterface = new LocalStorage();
        storageInterface.setBasePath(basePath);
        storageInterface.init();
    }

    @Test
    void list() throws IOException {
        Instant before = Instant.now().minusMillis(100);
        InternalKVStore kv = kv();

        assertThat(kv.list().size(), is(0));

        kv.put("my-key", new KVStoreValueWrapper<>(new KVMetadata(Duration.ofMinutes(5)), complexValue));
        kv.put("my-second-key", new KVStoreValueWrapper<>(new KVMetadata(Duration.ofMinutes(10)), complexValue));
        kv.put("expired-key", new KVStoreValueWrapper<>(new KVMetadata(Duration.ofMillis(1)), complexValue));
        Instant after = Instant.now().plusMillis(100);

        List<KVEntry> list = kv.list();
        assertThat(list.size(), is(2));

        list.forEach(kvEntry -> {
            assertThat(kvEntry.creationDate().isAfter(before) && kvEntry.creationDate().isBefore(after), is(true));
            assertThat(kvEntry.updateDate().isAfter(before) && kvEntry.updateDate().isBefore(after), is(true));
        });

        Map<String, KVEntry> map = list.stream().collect(Collectors.toMap(KVEntry::key, Function.identity()));
        // Check that we don't list expired keys
        assertThat(map.size(), is(2));

        KVEntry myKeyValue = map.get("my-key");
        assertThat(
            myKeyValue.creationDate().plus(Duration.ofMinutes(4)).isBefore(myKeyValue.expirationDate()) &&
                myKeyValue.creationDate().plus(Duration.ofMinutes(6)).isAfter(myKeyValue.expirationDate()),
            is(true)
        );

        KVEntry mySecondKeyValue = map.get("my-second-key");
        assertThat(
            mySecondKeyValue.creationDate().plus(Duration.ofMinutes(9)).isBefore(mySecondKeyValue.expirationDate()) &&
                mySecondKeyValue.creationDate().plus(Duration.ofMinutes(11)).isAfter(mySecondKeyValue.expirationDate()),
            is(true)
        );
    }

    @Test
    void put() throws IOException {
        // Given
        final InternalKVStore kv = kv();

        // When
        Instant before = Instant.now();
        kv.put("my-key", new KVStoreValueWrapper<>(new KVMetadata(Duration.ofMinutes(5)), complexValue));

        // Then
        StorageObject withMetadata = storageInterface.getWithMetadata(null, URI.create("/" + kv.namespace().replace(".", "/") + "/_kv/my-key.ion"));
        String valueFile = new String(withMetadata.inputStream().readAllBytes());
        Instant expirationDate = Instant.parse(withMetadata.metadata().get("expirationDate"));
        assertThat(expirationDate.isAfter(before.plus(Duration.ofMinutes(4))) && expirationDate.isBefore(before.plus(Duration.ofMinutes(6))), is(true));
        assertThat(valueFile, is(JacksonMapper.ofIon().writeValueAsString(complexValue)));

        // Re-When
        kv.put("my-key", new KVStoreValueWrapper<>(new KVMetadata(Duration.ofMinutes(10)), "some-value"));

        // Then
        withMetadata = storageInterface.getWithMetadata(null, URI.create("/" + kv.namespace().replace(".", "/") + "/_kv/my-key.ion"));
        valueFile = new String(withMetadata.inputStream().readAllBytes());
        expirationDate = Instant.parse(withMetadata.metadata().get("expirationDate"));
        assertThat(expirationDate.isAfter(before.plus(Duration.ofMinutes(9))) && expirationDate.isBefore(before.plus(Duration.ofMinutes(11))), is(true));
        assertThat(valueFile, is("\"some-value\""));
    }

    @Test
    void get() throws IOException, ResourceExpiredException {
        // Given
        final InternalKVStore kv = kv();
        kv.put("my-key", new KVStoreValueWrapper<>(new KVMetadata(Duration.ofMinutes(5)), complexValue));

        // When
        Optional<Object> value = kv.getValue("my-key");

        // Then
        assertThat(value.get(), is(complexValue));
    }

    @Test
    void getUnknownKey() throws IOException, ResourceExpiredException {
        // Given
        final InternalKVStore kv = kv();

        // When
        Optional<Object> value = kv.getValue("my-key");

        // Then
        assertThat(value.isEmpty(), is(true));
    }

    @Test
    void getExpiredKV() throws IOException {
        // Given
        final InternalKVStore kv = kv();
        kv.put("my-key", new KVStoreValueWrapper<>(new KVMetadata(Duration.ofNanos(1)), complexValue));

        // When
        Assertions.assertThrows(ResourceExpiredException.class, () -> kv.getValue("my-key"));
    }

    @Test
    void illegalKey() {
        InternalKVStore kv = kv();
        String expectedErrorMessage = "Key must start with an alphanumeric character (uppercase or lowercase) and can contain alphanumeric characters (uppercase or lowercase), dots (.), underscores (_), and hyphens (-) only.";

        IllegalArgumentException illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class, () -> kv.validateKey("a/b"));
        assertThat(illegalArgumentException.getMessage(), is(expectedErrorMessage));
        illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class, () -> kv.getValue("a/b"));
        assertThat(illegalArgumentException.getMessage(), is(expectedErrorMessage));
        illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class, () -> kv.put("a/b", new KVStoreValueWrapper<>(new KVMetadata(Duration.ofMinutes(5)), "content")));
        assertThat(illegalArgumentException.getMessage(), is(expectedErrorMessage));
        illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class, () -> kv.delete("a/b"));
        assertThat(illegalArgumentException.getMessage(), is(expectedErrorMessage));

        Assertions.assertDoesNotThrow(() -> kv.validateKey("AN_UPPER.CASE-key"));
    }

    private InternalKVStore kv() {
        final String namespaceId = "io.kestra." + IdUtils.create();
        return new InternalKVStore(null, namespaceId, storageInterface);
    }
}