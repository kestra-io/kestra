package io.kestra.webserver.controllers.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.within;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.kv.KVType;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.StorageObject;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.webserver.controllers.api.KVController.ApiDeleteBulkRequest;
import io.kestra.webserver.controllers.api.KVController.ApiDeleteBulkResponse;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@KestraTest(resolveParameters = false)
class KVControllerTest {

    private static final String NAMESPACE = "io.namespace";
    public static final String TENANT_ID = "main";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    private StorageInterface storageInterface;

    @BeforeEach
    public void init() throws IOException {
        storageInterface.delete(TENANT_ID, NAMESPACE, toKVUri(NAMESPACE, null));
    }

    @SuppressWarnings("unchecked")
    @Test
    void listKeys() throws IOException {
        Instant myKeyExpirationDate = Instant.now().plus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MILLIS);
        Instant mySecondKeyExpirationDate = Instant.now().plus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.MILLIS);
        storageInterface.put(TENANT_ID, NAMESPACE, toKVUri(NAMESPACE, "my-key"), new StorageObject(Map.of("expirationDate", myKeyExpirationDate.toString()), new ByteArrayInputStream("my-value".getBytes())));
        String secondKvDescription = "myDescription";
        storageInterface.put(TENANT_ID, NAMESPACE, toKVUri(NAMESPACE, "my-second-key"), new StorageObject(Map.of("expirationDate", mySecondKeyExpirationDate.toString(), "description", secondKvDescription), new ByteArrayInputStream("my-second-value".getBytes())));

        List<KVEntry> res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + NAMESPACE + "/kv"), Argument.of(List.class, KVEntry.class));
        res.stream().forEach(entry -> {
            assertThat(entry.creationDate()).isCloseTo(Instant.now(), within(1, ChronoUnit.SECONDS));
            assertThat(entry.updateDate()).isCloseTo(Instant.now(), within(1, ChronoUnit.SECONDS));
        });

        assertThat(res.stream().filter(entry -> entry.key().equals("my-key")).findFirst().get().expirationDate()).isEqualTo(myKeyExpirationDate);
        KVEntry secondKv = res.stream().filter(entry -> entry.key().equals("my-second-key")).findFirst().get();
        assertThat(secondKv.expirationDate()).isEqualTo(mySecondKeyExpirationDate);
        assertThat(secondKv.description()).isEqualTo(secondKvDescription);
    }

    static Stream<Arguments> kvGetKeyValueArgs() {
        return Stream.of(
            Arguments.of("{hello:\"world\"}", KVType.JSON, "{\"hello\":\"world\"}"),
            Arguments.of("[\"hello\",\"world\"]", KVType.JSON, "[\"hello\",\"world\"]"),
            Arguments.of("\"hello\"", KVType.STRING, "\"hello\""),
            Arguments.of("1", KVType.NUMBER, "1"),
            Arguments.of("1.0", KVType.NUMBER, "1.0"),
            Arguments.of("true", KVType.BOOLEAN, "true"),
            Arguments.of("false", KVType.BOOLEAN, "false"),
            Arguments.of("2021-09-01", KVType.DATE, "\"2021-09-01\""),
            Arguments.of("2021-09-01T01:02:03Z", KVType.DATETIME, "\"2021-09-01T01:02:03Z\""),
            Arguments.of("\"PT5S\"", KVType.DURATION, "\"PT5S\"")
        );
    }

    @ParameterizedTest
    @MethodSource("kvGetKeyValueArgs")
    void getKeyValue(String storedIonValue, KVType expectedType, String expectedValue) throws IOException {
        storageInterface.put(
            TENANT_ID,
            NAMESPACE,
            toKVUri(NAMESPACE, "my-key"),
            new StorageObject(
                Map.of("expirationDate", Instant.now().plus(Duration.ofMinutes(5)).toString()),
                new ByteArrayInputStream(storedIonValue.getBytes())
            )
        );

        String res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + NAMESPACE + "/kv/my-key"), String.class);
        assertThat(res).contains("\"type\":\"" + expectedType + "\"");
        assertThat(res).contains("\"value\":" + expectedValue);
    }

    @Test
    void getKeyValueNotFound() {
        HttpClientResponseException httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + NAMESPACE + "/kv/my-key")));
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(httpClientResponseException.getMessage()).isEqualTo("Not Found: No value found for key 'my-key' in namespace '" + NAMESPACE + "'");
    }

    @Test
    void getKeyValueExpired() throws IOException {
        storageInterface.put(
            TENANT_ID,
            NAMESPACE,
            toKVUri(NAMESPACE, "my-key"),
            new StorageObject(
                Map.of("expirationDate", Instant.now().minus(Duration.ofMinutes(5)).toString()),
                new ByteArrayInputStream("value".getBytes())
            )
        );

        HttpClientResponseException httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + NAMESPACE + "/kv/my-key")));
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.GONE.getCode());
        assertThat(httpClientResponseException.getMessage()).isEqualTo("Resource has expired: The requested value has expired");
    }

    static Stream<Arguments> kvSetKeyValueArgs() {
        return Stream.of(
            Arguments.of(MediaType.APPLICATION_JSON, "{\"hello\":\"world\"}", Map.class),
            Arguments.of(MediaType.APPLICATION_JSON, "[\"hello\",\"world\"]", List.class),
            Arguments.of(MediaType.APPLICATION_JSON, "\"hello\"", String.class),
            Arguments.of(MediaType.APPLICATION_JSON, "1", Integer.class),
            Arguments.of(MediaType.APPLICATION_JSON, "1.0", BigDecimal.class),
            Arguments.of(MediaType.APPLICATION_JSON, "true", Boolean.class),
            Arguments.of(MediaType.APPLICATION_JSON, "false", Boolean.class),
            Arguments.of(MediaType.APPLICATION_JSON, "2021-09-01", LocalDate.class),
            Arguments.of(MediaType.APPLICATION_JSON, "2021-09-01T01:02:03Z", Instant.class),
            Arguments.of(MediaType.APPLICATION_JSON, "\"PT5S\"", Duration.class)
        );
    }

    @ParameterizedTest
    @MethodSource("kvSetKeyValueArgs")
    void setKeyValue(MediaType mediaType, String value, Class<?> expectedClass) throws IOException, ResourceExpiredException {
        String myDescription = "myDescription";
        client.toBlocking().exchange(HttpRequest.PUT("/api/v1/main/namespaces/" + NAMESPACE + "/kv/my-key", value).contentType(mediaType).header("ttl", "PT5M").header("description", myDescription));

        KVStore kvStore = new InternalKVStore(TENANT_ID, NAMESPACE, storageInterface);
        Class<?> valueClazz = kvStore.getValue("my-key").get().value().getClass();
        assertThat(expectedClass.isAssignableFrom(valueClazz)).as("Expected value to be a " + expectedClass + " but was " + valueClazz).isTrue();

        List<KVEntry> list = kvStore.list();
        assertThat(list.size()).isEqualTo(1);
        KVEntry kvEntry = list.get(0);
        assertThat(kvEntry.expirationDate().isAfter(Instant.now().plus(Duration.ofMinutes(4)))).isTrue();
        assertThat(kvEntry.expirationDate().isBefore(Instant.now().plus(Duration.ofMinutes(6)))).isTrue();
        assertThat(kvEntry.description()).isEqualTo(myDescription);
    }

    @Test
    void deleteKeyValue() throws IOException {
        storageInterface.put(
            TENANT_ID,
            NAMESPACE,
            toKVUri(NAMESPACE, "my-key"),
            new StorageObject(
                Map.of("expirationDate", Instant.now().plus(Duration.ofMinutes(5)).toString()),
                new ByteArrayInputStream("\"content\"".getBytes())
            )
        );

        assertThat(storageInterface.exists(TENANT_ID, NAMESPACE, toKVUri(NAMESPACE, "my-key"))).isTrue();
        client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/main/namespaces/" + NAMESPACE + "/kv/my-key"));

        assertThat(storageInterface.exists(TENANT_ID, NAMESPACE, toKVUri(NAMESPACE, "my-key"))).isFalse();
    }

    @Test
    void shouldReturnSuccessForDeleteKeyValueBulkOperationGivenExistingKeys() throws IOException {
        // Given
        storageInterface.put(
            TENANT_ID,
            NAMESPACE,
            toKVUri(NAMESPACE, "my-key"),
            new StorageObject(
                Map.of("expirationDate", Instant.now().plus(Duration.ofMinutes(5)).toString()),
                new ByteArrayInputStream("\"content\"".getBytes())
            )
        );
        assertThat(storageInterface.exists(TENANT_ID, NAMESPACE, toKVUri(NAMESPACE, "my-key"))).isTrue();

        // When
        HttpResponse<ApiDeleteBulkResponse> response = client.toBlocking()
            .exchange(HttpRequest.DELETE("/api/v1/main/namespaces/" + NAMESPACE + "/kv", new ApiDeleteBulkRequest(List.of("my-key"))), ApiDeleteBulkResponse.class);

        // Then
        Assertions.assertEquals(HttpStatus.OK, response.getStatus());
        Assertions.assertEquals(new ApiDeleteBulkResponse(List.of("my-key")), response.body());
    }

    @Test
    void shouldReturnSuccessForDeleteKeyValueBulkOperationGivenNonExistingKeys() {
        // Given
        // When
        HttpResponse<ApiDeleteBulkResponse> response = client.toBlocking()
            .exchange(HttpRequest.DELETE("/api/v1/main/namespaces/" + NAMESPACE + "/kv", new ApiDeleteBulkRequest(List.of("my-key"))), ApiDeleteBulkResponse.class);

        // Then
        Assertions.assertEquals(HttpStatus.OK, response.getStatus());
        Assertions.assertEquals(new ApiDeleteBulkResponse(List.of()), response.body());
        assertThat(storageInterface.exists(null, NAMESPACE, toKVUri(NAMESPACE, "my-key"))).isFalse();
    }

    @Test
    void illegalKey() {
        String expectedErrorMessage = "Illegal argument: Key must start with an alphanumeric character (uppercase or lowercase) and can contain alphanumeric characters (uppercase or lowercase), dots (.), underscores (_), and hyphens (-) only.";

        HttpClientResponseException httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + NAMESPACE + "/kv/bad$key")));
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
        assertThat(httpClientResponseException.getMessage()).isEqualTo(expectedErrorMessage);

        httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.PUT("/api/v1/main/namespaces/" + NAMESPACE + "/kv/bad$key", "\"content\"").contentType(MediaType.APPLICATION_JSON)));
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
        assertThat(httpClientResponseException.getMessage()).isEqualTo(expectedErrorMessage);

        httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.DELETE("/api/v1/main/namespaces/" + NAMESPACE + "/kv/bad$key")));
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
        assertThat(httpClientResponseException.getMessage()).isEqualTo(expectedErrorMessage);
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
