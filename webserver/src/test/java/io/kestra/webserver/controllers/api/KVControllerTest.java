package io.kestra.webserver.controllers.api;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.kv.KVType;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.*;
import io.kestra.core.utils.TestsUtils;
import io.kestra.webserver.controllers.api.KVController.ApiDeleteBulkRequest;
import io.kestra.webserver.controllers.api.KVController.ApiDeleteBulkResponse;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.within;

@KestraTest(resolveParameters = false)
class KVControllerTest {

    private static final String NAMESPACE = "io.namespace";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private KvMetadataRepositoryInterface kvMetadataRepository;

    @BeforeEach
    public void init() throws IOException {
        storageInterface.delete(MAIN_TENANT, NAMESPACE, toKVUri(NAMESPACE, null));
        List<PersistedKvMetadata> persistedKvMetadata = kvMetadataRepository.find(Pageable.UNPAGED, MAIN_TENANT, Collections.emptyList(), true, true);
        kvMetadataRepository.purge(persistedKvMetadata);
    }

    @SuppressWarnings("unchecked")
    @Test
    void listAllKeys() throws IOException {
        String namespace = TestsUtils.randomNamespace();
        KVStore kvStore = new InternalKVStore(MAIN_TENANT, namespace, storageInterface, kvMetadataRepository);
        String secondNamespace = TestsUtils.randomNamespace();
        KVStore secondKvStore = new InternalKVStore(MAIN_TENANT, secondNamespace, storageInterface, kvMetadataRepository);

        // Should come first in key:desc order
        String namespaceKey = "namespace-key";
        String namespaceDescription = "namespaceDescription";
        Instant beforeInsertion = Instant.now();
        Instant expirationDate = Instant.now().plus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MILLIS);
        kvStore.put(namespaceKey, new KVValueAndMetadata(new KVMetadata(namespaceDescription, expirationDate), "namespace-value"));
        Instant afterInsertion = Instant.now();
        // Expired key, should not be listed
        kvStore.put("z-expired-key", new KVValueAndMetadata(new KVMetadata(null, Instant.now().minus(1, ChronoUnit.HOURS)), "expired-value"));
        String secondNamespaceKey = "another-namespace-key";
        secondKvStore.put(secondNamespaceKey, new KVValueAndMetadata(new KVMetadata("anotherNamespaceDescription", Instant.now().plus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.MILLIS)), "another-namespace-value"));

        PagedResults<KVEntry> res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/kv?size=1&page=1&sort=key:desc"), Argument.of(PagedResults.class, KVEntry.class));

        assertThat(res.getTotal()).isEqualTo(2);
        assertThat(res.getResults().size()).isEqualTo(1);
        KVEntry descOrderKvEntry = res.getResults().getFirst();
        assertThat(descOrderKvEntry.namespace()).isEqualTo(namespace);
        assertThat(descOrderKvEntry.key()).isEqualTo(namespaceKey);
        assertThat(descOrderKvEntry.description()).isEqualTo(namespaceDescription);
        assertThat(descOrderKvEntry.creationDate()).isBetween(beforeInsertion, afterInsertion);
        assertThat(descOrderKvEntry.updateDate()).isBetween(beforeInsertion, afterInsertion);
        assertThat(descOrderKvEntry.expirationDate()).isEqualTo(expirationDate);

        res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/kv?size=1&page=2&sort=key:desc"), Argument.of(PagedResults.class, KVEntry.class));
        assertThat(res.getTotal()).isEqualTo(2);
        assertThat(res.getResults().size()).isEqualTo(1);
        assertThat(res.getResults().getFirst().namespace()).isEqualTo(secondNamespace);
        assertThat(res.getResults().getFirst().key()).isEqualTo(secondNamespaceKey);

        secondKvStore.delete(secondNamespaceKey);
        res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/kv?size=1&page=1&sort=key:desc"), Argument.of(PagedResults.class, KVEntry.class));
        assertThat(res.getTotal()).isEqualTo(1);
        assertThat(res.getResults().size()).isEqualTo(1);
        assertThat(res.getResults().getFirst().namespace()).isEqualTo(namespace);
        assertThat(res.getResults().getFirst().key()).isEqualTo(namespaceKey);
    }

    @SuppressWarnings("unchecked")
    @Test
    void listKeys() throws IOException {
        Instant myKeyExpirationDate = Instant.now().plus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MILLIS);
        Instant mySecondKeyExpirationDate = Instant.now().plus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.MILLIS);
        kvStore().put("my-key", new KVValueAndMetadata(new KVMetadata(null, myKeyExpirationDate), "my-value"));
        String secondKvDescription = "myDescription";
        kvStore().put("my-second-key", new KVValueAndMetadata(new KVMetadata(secondKvDescription, mySecondKeyExpirationDate), "my-second-value"));

        List<KVEntry> res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + NAMESPACE + "/kv"), Argument.of(List.class, KVEntry.class));
        res.forEach(entry -> {
            assertThat(entry.creationDate()).isCloseTo(Instant.now(), within(1, ChronoUnit.SECONDS));
            assertThat(entry.updateDate()).isCloseTo(Instant.now(), within(1, ChronoUnit.SECONDS));
        });

        assertThat(res.stream().filter(entry -> entry.key().equals("my-key")).findFirst().get().expirationDate()).isEqualTo(myKeyExpirationDate);
        KVEntry secondKv = res.stream().filter(entry -> entry.key().equals("my-second-key")).findFirst().get();
        assertThat(secondKv.expirationDate()).isEqualTo(mySecondKeyExpirationDate);
        assertThat(secondKv.description()).isEqualTo(secondKvDescription);
    }

    @Test
    void listKeysWithInheritance() throws IOException {
        Instant myKeyExpirationDate = Instant.now().plus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MILLIS);
        String namespaceParent = "io";
        String namespaceDescription = "in the namespace";
        String namespaceParentDescription = "in the parent namespace";

        kvStore().put("shared-key", new KVValueAndMetadata(new KVMetadata(namespaceDescription, myKeyExpirationDate), "my-value"));
        kvStore().put("child-key", new KVValueAndMetadata(new KVMetadata(namespaceDescription, myKeyExpirationDate), "my-second-value"));

        kvStore(namespaceParent).put("shared-key", new KVValueAndMetadata(new KVMetadata(namespaceParentDescription, myKeyExpirationDate), "my-value"));
        kvStore(namespaceParent).put("parent-key", new KVValueAndMetadata(new KVMetadata(namespaceParentDescription, myKeyExpirationDate), "my-second-value"));

        List<KVEntry> res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + NAMESPACE + "/kv/inheritance"), Argument.of(List.class, KVEntry.class));

        assertThat(res).hasSize(2);
        Map<String, String> keyDescriptions = res.stream()
            .collect(Collectors.toMap(KVEntry::key, KVEntry::description));
        assertThat(keyDescriptions).isEqualTo(Map.of("shared-key", namespaceParentDescription,
            "parent-key", namespaceParentDescription));

    }

    static Stream<Arguments> kvGetKeyValueArgs() {
        return Stream.of(
            Arguments.of(Map.of("hello", "world"), KVType.JSON, "{\"hello\":\"world\"}"),
            Arguments.of(List.of("hello", "world"), KVType.JSON, "[\"hello\",\"world\"]"),
            Arguments.of("hello", KVType.STRING, "\"hello\""),
            Arguments.of(1, KVType.NUMBER, "1"),
            Arguments.of(1.1, KVType.NUMBER, "1.1"),
            Arguments.of(true, KVType.BOOLEAN, "true"),
            Arguments.of(false, KVType.BOOLEAN, "false"),
            Arguments.of(LocalDate.parse("2021-09-01"), KVType.DATE, "\"2021-09-01\""),
            Arguments.of(Instant.parse("2021-09-01T01:02:03Z"), KVType.DATETIME, "\"2021-09-01T01:02:03Z\""),
            Arguments.of(Duration.ofSeconds(5), KVType.DURATION, "\"PT5S\"")
        );
    }

    @ParameterizedTest
    @MethodSource("kvGetKeyValueArgs")
    void getKeyValue(Object value, KVType expectedType, String expectedValue) throws IOException {
        kvStore().put("my-key", new KVValueAndMetadata(new KVMetadata(null, Instant.now().plus(Duration.ofMinutes(5))), value));

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
        kvStore().put("my-key", new KVValueAndMetadata(new KVMetadata(null, Instant.now().minus(Duration.ofMinutes(5))), "value"));

        HttpClientResponseException httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + NAMESPACE + "/kv/my-key")));
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.GONE.getCode());
        assertThat(httpClientResponseException.getMessage()).isEqualTo("Resource has expired: The requested value has expired");
    }

    static Stream<Arguments> kvSetKeyValueArgs() {
        return Stream.of(
            Arguments.of(MediaType.TEXT_PLAIN, "{\"hello\":\"world\"}", Map.class),
            Arguments.of(MediaType.TEXT_PLAIN, "[\"hello\",\"world\"]", List.class),
            Arguments.of(MediaType.TEXT_PLAIN, "\"hello\"", String.class),
            Arguments.of(MediaType.TEXT_PLAIN, "1", Integer.class),
            Arguments.of(MediaType.TEXT_PLAIN, "1.0", BigDecimal.class),
            Arguments.of(MediaType.TEXT_PLAIN, "true", Boolean.class),
            Arguments.of(MediaType.TEXT_PLAIN, "false", Boolean.class),
            Arguments.of(MediaType.TEXT_PLAIN, "2021-09-01", LocalDate.class),
            Arguments.of(MediaType.TEXT_PLAIN, "2021-09-01T01:02:03Z", Instant.class),
            Arguments.of(MediaType.TEXT_PLAIN, "\"PT5S\"", Duration.class)
        );
    }

    @ParameterizedTest
    @MethodSource("kvSetKeyValueArgs")
    void setKeyValue(MediaType mediaType, String value, Class<?> expectedClass) throws IOException, ResourceExpiredException {
        String myDescription = "myDescription";
        client.toBlocking().exchange(HttpRequest.PUT("/api/v1/main/namespaces/" + NAMESPACE + "/kv/my-key", value).contentType(mediaType).header("ttl", "PT5M").header("description", myDescription));

        KVStore kvStore = kvStore();
        Class<?> valueClazz = kvStore.getValue("my-key").get().value().getClass();
        assertThat(expectedClass.isAssignableFrom(valueClazz)).as("Expected value to be a " + expectedClass + " but was " + valueClazz).isTrue();

        List<KVEntry> list = kvStore.list();
        assertThat(list.size()).isEqualTo(1);
        KVEntry kvEntry = list.get(0);
        assertThat(kvEntry.expirationDate().isAfter(Instant.now().plus(Duration.ofMinutes(4)))).isTrue();
        assertThat(kvEntry.expirationDate().isBefore(Instant.now().plus(Duration.ofMinutes(6)))).isTrue();
        assertThat(kvEntry.description()).isEqualTo(myDescription);
    }

    private InternalKVStore kvStore() {
        return this.kvStore(NAMESPACE);
    }

    private InternalKVStore kvStore(String namespace) {
        return new InternalKVStore(MAIN_TENANT, namespace, storageInterface, kvMetadataRepository);
    }

    @Test
    void deleteKeyValue() throws IOException {
        InternalKVStore kvStore = kvStore();
        kvStore.put("my-key", new KVValueAndMetadata(new KVMetadata(null, Instant.now().plus(Duration.ofMinutes(5))), "content"));

        assertThat(kvStore.exists("my-key")).isTrue();
        client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/main/namespaces/" + NAMESPACE + "/kv/my-key"));

        assertThat(kvStore.exists("my-key")).isFalse();
        // Soft delete, storage object still exists, purge must be used to fully delete it
        assertThat(storageInterface.exists(MAIN_TENANT, NAMESPACE, toKVUri(NAMESPACE, "my-key"))).isTrue();
    }

    @Test
    void shouldReturnSuccessForDeleteKeyValueBulkOperationGivenExistingKeys() throws IOException {
        // Given
        InternalKVStore kvStore = kvStore();
        kvStore.put("my-key", new KVValueAndMetadata(new KVMetadata(null, Instant.now().plus(Duration.ofMinutes(5))), "content"));
        assertThat(kvStore.exists("my-key")).isTrue();

        // When
        HttpResponse<ApiDeleteBulkResponse> response = client.toBlocking()
            .exchange(HttpRequest.DELETE("/api/v1/main/namespaces/" + NAMESPACE + "/kv", new ApiDeleteBulkRequest(List.of("my-key"))), ApiDeleteBulkResponse.class);

        // Then
        Assertions.assertEquals(HttpStatus.OK, response.getStatus());
        Assertions.assertEquals(new ApiDeleteBulkResponse(List.of("my-key")), response.body());

        assertThat(kvStore.exists("my-key")).isFalse();
    }

    @Test
    void shouldReturnSuccessForDeleteKeyValueBulkOperationGivenNonExistingKeys() throws IOException {
        // When
        HttpResponse<ApiDeleteBulkResponse> response = client.toBlocking()
            .exchange(HttpRequest.DELETE("/api/v1/main/namespaces/" + NAMESPACE + "/kv", new ApiDeleteBulkRequest(List.of("my-key"))), ApiDeleteBulkResponse.class);

        // Then
        Assertions.assertEquals(HttpStatus.OK, response.getStatus());
        Assertions.assertEquals(new ApiDeleteBulkResponse(List.of()), response.body());

        assertThat(kvStore().exists("my-key")).isFalse();
        assertThat(storageInterface.exists(MAIN_TENANT, NAMESPACE, toKVUri(NAMESPACE, "my-key"))).isFalse();
    }

    @Test
    void illegalKey() {
        String expectedErrorMessage = "Illegal argument: Key must start with an alphanumeric character (uppercase or lowercase) and can contain alphanumeric characters (uppercase or lowercase), dots (.), underscores (_), and hyphens (-) only.";

        HttpClientResponseException httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + NAMESPACE + "/kv/bad$key")));
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
        assertThat(httpClientResponseException.getMessage()).isEqualTo(expectedErrorMessage);

        httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.PUT("/api/v1/main/namespaces/" + NAMESPACE + "/kv/bad$key", "\"content\"").contentType(MediaType.TEXT_PLAIN)));
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
        assertThat(httpClientResponseException.getMessage()).isEqualTo(expectedErrorMessage);

        httpClientResponseException = Assertions.assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.DELETE("/api/v1/main/namespaces/" + NAMESPACE + "/kv/bad$key")));
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
        assertThat(httpClientResponseException.getMessage()).isEqualTo(expectedErrorMessage);
    }

    @Test
    void jsonFallback() throws IOException, ResourceExpiredException {

        client.toBlocking().exchange(
                HttpRequest.PUT("/api/v1/main/namespaces/" + NAMESPACE + "/kv/my-key", "1.2.3")
                        .contentType(MediaType.TEXT_PLAIN)
        );

        KVStore kvStore = kvStore();
        Object stored = kvStore.getValue("my-key").orElseThrow().value();
        assertThat(stored).isInstanceOf(String.class);
        assertThat(stored).isEqualTo("1.2.3");
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
