package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.StorageObject;
import io.kestra.core.storages.kv.*;
import io.kestra.plugin.core.flow.Subflow;
import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;

@KestraTest
class KVControllerTest extends JdbcH2ControllerTest {
    private static final String NAMESPACE = "io.namespace";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    private StorageInterface storageInterface;

    @BeforeEach
    public void init() throws IOException {
        storageInterface.delete(null, toKVUri(NAMESPACE, null));

        super.setup();
    }

    @SuppressWarnings("unchecked")
    @Test
    void list() throws IOException {
        Instant before = Instant.now().minusMillis(100);
        Instant myKeyExpirationDate = Instant.now().plus(Duration.ofMinutes(5));
        Instant mySecondKeyExpirationDate = Instant.now().plus(Duration.ofMinutes(10));
        storageInterface.put(null, toKVUri(NAMESPACE, "my-key"), new StorageObject(Map.of("expirationDate", myKeyExpirationDate.toString()), new ByteArrayInputStream("my-value".getBytes())));
        storageInterface.put(null, toKVUri(NAMESPACE, "my-second-key"), new StorageObject(Map.of("expirationDate", mySecondKeyExpirationDate.toString()), new ByteArrayInputStream("my-second-value".getBytes())));
        Instant after = Instant.now().plusMillis(100);

        List<KVEntry> res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/kv"), Argument.of(List.class, KVEntry.class));
        res.stream().forEach(entry -> {
            assertThat(entry.creationDate().isAfter(before) && entry.creationDate().isBefore(after), is(true));
            assertThat(entry.updateDate().isAfter(before) && entry.updateDate().isBefore(after), is(true));
        });

        assertThat(res.stream().filter(entry -> entry.key().equals("my-key")).findFirst().get().expirationDate(), is(myKeyExpirationDate));
        assertThat(res.stream().filter(entry -> entry.key().equals("my-second-key")).findFirst().get().expirationDate(), is(mySecondKeyExpirationDate));
    }

    @Test
    void get() throws IOException {
        String myKeyStoredValue = JacksonMapper.ofIon().writeValueAsString(List.of(Map.of("key", "value"), "some-value"));
        storageInterface.put(
            null,
            toKVUri(NAMESPACE, "my-key"),
            new StorageObject(
                Map.of("expirationDate", Instant.now().plus(Duration.ofMinutes(5)).toString()),
                new ByteArrayInputStream(myKeyStoredValue.getBytes())
            )
        );
        String mySecondKeyStoredValue = JacksonMapper.ofIon().writeValueAsString(Map.of("some", "value", "nested", Map.of("key", "value")));
        storageInterface.put(
            null,
            toKVUri(NAMESPACE, "my-second-key"),
            new StorageObject(
                Map.of("expirationDate", Instant.now().plus(Duration.ofMinutes(10)).toString()),
                new ByteArrayInputStream(mySecondKeyStoredValue.getBytes())
            )
        );

        Object res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/kv/my-key"));
        assertThat(res, is(myKeyStoredValue));
        res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/kv/my-second-key"));
        assertThat(res, is(mySecondKeyStoredValue));
    }

    @Test
    void getNotFound() {
        HttpClientResponseException httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/kv/my-key")));
        assertThat(httpClientResponseException.getStatus(), is(HttpStatus.NOT_FOUND));
        assertThat(httpClientResponseException.getMessage(), is("Not Found: No value found for key 'my-key' in namespace '" + NAMESPACE + "'"));
    }

    @Test
    void getExpired() throws IOException {
        storageInterface.put(
            null,
            toKVUri(NAMESPACE, "my-key"),
            new StorageObject(
                Map.of("expirationDate", Instant.now().minus(Duration.ofMinutes(5)).toString()),
                new ByteArrayInputStream("value".getBytes())
            )
        );

        HttpClientResponseException httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/kv/my-key")));
        assertThat(httpClientResponseException.getStatus().getCode(), is(HttpStatus.GONE.getCode()));
        assertThat(httpClientResponseException.getMessage(), is("Resource has expired: The requested value has expired"));
    }

    @Test
    void put() throws IOException {
        String myKeyStoredValue = JacksonMapper.ofIon().writeValueAsString(List.of(Map.of("key", "value"), "some-value"));
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/namespaces/" + NAMESPACE + "/kv/my-key", myKeyStoredValue).contentType(MediaType.APPLICATION_OCTET_STREAM).header("ttl", "PT5M"));

        Object res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/kv/my-key"));
        assertThat(res, is(myKeyStoredValue));

        KVStore kvStore = new InternalKVStore(null, NAMESPACE, storageInterface);
        List<KVEntry> list = kvStore.list();
        assertThat(list.size(), is(1));
        KVEntry kvEntry = list.get(0);
        assertThat(kvEntry.expirationDate().isAfter(Instant.now().plus(Duration.ofMinutes(4))), is(true));
        assertThat(kvEntry.expirationDate().isBefore(Instant.now().plus(Duration.ofMinutes(6))), is(true));
    }

    @Test
    void delete() throws IOException {
        storageInterface.put(
            null,
            toKVUri(NAMESPACE, "my-key"),
            new StorageObject(
                Map.of("expirationDate", Instant.now().plus(Duration.ofMinutes(5)).toString()),
                new ByteArrayInputStream("\"content\"".getBytes())
            )
        );

        assertThat(storageInterface.exists(null, toKVUri(NAMESPACE, "my-key")), is(true));
        client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/namespaces/" + NAMESPACE + "/kv/my-key"));

        assertThat(storageInterface.exists(null, toKVUri(NAMESPACE, "my-key")), is(false));
    }

    @Test
    void illegalKey() {
        String expectedErrorMessage = "Illegal argument: Key must start with an alphanumeric character (uppercase or lowercase) and can contain alphanumeric characters (uppercase or lowercase), dots (.), underscores (_), and hyphens (-) only.";

        HttpClientResponseException httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/kv/bad$key")));
        assertThat(httpClientResponseException.getStatus().getCode(), is(HttpStatus.UNPROCESSABLE_ENTITY.getCode()));
        assertThat(httpClientResponseException.getMessage(), Matchers.is(expectedErrorMessage));

        httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.POST("/api/v1/namespaces/" + NAMESPACE + "/kv/bad$key", "\"content\"").contentType(MediaType.APPLICATION_OCTET_STREAM)));
        assertThat(httpClientResponseException.getStatus().getCode(), is(HttpStatus.UNPROCESSABLE_ENTITY.getCode()));
        assertThat(httpClientResponseException.getMessage(), Matchers.is(expectedErrorMessage));

        httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.DELETE("/api/v1/namespaces/" + NAMESPACE + "/kv/bad$key")));
        assertThat(httpClientResponseException.getStatus().getCode(), is(HttpStatus.UNPROCESSABLE_ENTITY.getCode()));
        assertThat(httpClientResponseException.getMessage(), Matchers.is(expectedErrorMessage));
    }

    private URI toKVUri(String namespace, String key) {
        String slashLedKey;
        if (key == null) {
            slashLedKey = "";
        } else {
            slashLedKey = key.startsWith("/") ? key : "/" + key;
            slashLedKey += ".ion";
        }
        return URI.create("/" + namespace.replace(".", "/") + "/_kv" + slashLedKey);
    }
}