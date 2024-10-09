package io.kestra.webserver.controllers.api;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.kv.KVType;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.StorageObject;
import io.kestra.core.storages.kv.*;
import io.kestra.webserver.controllers.api.KVController.ApiDeleteBulkRequest;
import io.kestra.webserver.controllers.api.KVController.ApiDeleteBulkResponse;
import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
import java.util.Set;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;

@KestraTest(resolveParameters = false)
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
        Instant myKeyExpirationDate = Instant.now().plus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MILLIS);
        Instant mySecondKeyExpirationDate = Instant.now().plus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.MILLIS);
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

    static Stream<Arguments> kvGetArgs() {
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
    @MethodSource("kvGetArgs")
    void get(String storedIonValue, KVType expectedType, String expectedValue) throws IOException {
        storageInterface.put(
            null,
            toKVUri(NAMESPACE, "my-key"),
            new StorageObject(
                Map.of("expirationDate", Instant.now().plus(Duration.ofMinutes(5)).toString()),
                new ByteArrayInputStream(storedIonValue.getBytes())
            )
        );

        String res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/kv/my-key"), String.class);
        assertThat(res, containsString("\"type\":\"" + expectedType + "\""));
        assertThat(res, containsString("\"value\":" + expectedValue));
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

    static Stream<Arguments> kvPutArgs() {
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
    @MethodSource("kvPutArgs")
    void put(MediaType mediaType, String value, Class<?> expectedClass) throws IOException, ResourceExpiredException {
        client.toBlocking().exchange(HttpRequest.PUT("/api/v1/namespaces/" + NAMESPACE + "/kv/my-key", value).contentType(mediaType).header("ttl", "PT5M"));

        KVStore kvStore = new InternalKVStore(null, NAMESPACE, storageInterface);
        Class<?> valueClazz = kvStore.getValue("my-key").get().value().getClass();
        assertThat("Expected value to be a " + expectedClass + " but was " + valueClazz, expectedClass.isAssignableFrom(valueClazz), is(true));

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
    void shouldReturnSuccessForDeleteBulkOperationGivenExistingKeys() throws IOException {
        // Given
        storageInterface.put(
            null,
            toKVUri(NAMESPACE, "my-key"),
            new StorageObject(
                Map.of("expirationDate", Instant.now().plus(Duration.ofMinutes(5)).toString()),
                new ByteArrayInputStream("\"content\"".getBytes())
            )
        );
        assertThat(storageInterface.exists(null, toKVUri(NAMESPACE, "my-key")), is(true));

        // When
        HttpResponse<ApiDeleteBulkResponse> response = client.toBlocking()
            .exchange(HttpRequest.DELETE("/api/v1/namespaces/" + NAMESPACE + "/kv", new ApiDeleteBulkRequest(List.of("my-key"))), ApiDeleteBulkResponse.class);

        // Then
        Assertions.assertEquals(HttpStatus.OK, response.getStatus());
        Assertions.assertEquals(new ApiDeleteBulkResponse(List.of("my-key")), response.body());
    }

    @Test
    void shouldReturnSuccessForDeleteBulkOperationGivenNonExistingKeys() {
        // Given
        // When
        HttpResponse<ApiDeleteBulkResponse> response = client.toBlocking()
            .exchange(HttpRequest.DELETE("/api/v1/namespaces/" + NAMESPACE + "/kv", new ApiDeleteBulkRequest(List.of("my-key"))), ApiDeleteBulkResponse.class);

        // Then
        Assertions.assertEquals(HttpStatus.OK, response.getStatus());
        Assertions.assertEquals(new ApiDeleteBulkResponse(List.of()), response.body());
        assertThat(storageInterface.exists(null, toKVUri(NAMESPACE, "my-key")), is(false));
    }

    @Test
    void illegalKey() {
        String expectedErrorMessage = "Illegal argument: Key must start with an alphanumeric character (uppercase or lowercase) and can contain alphanumeric characters (uppercase or lowercase), dots (.), underscores (_), and hyphens (-) only.";

        HttpClientResponseException httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/namespaces/" + NAMESPACE + "/kv/bad$key")));
        assertThat(httpClientResponseException.getStatus().getCode(), is(HttpStatus.UNPROCESSABLE_ENTITY.getCode()));
        assertThat(httpClientResponseException.getMessage(), Matchers.is(expectedErrorMessage));

        httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.PUT("/api/v1/namespaces/" + NAMESPACE + "/kv/bad$key", "\"content\"").contentType(MediaType.APPLICATION_JSON)));
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