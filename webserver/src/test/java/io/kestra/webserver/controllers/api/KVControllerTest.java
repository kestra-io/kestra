package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.kv.KVType;
import io.kestra.core.models.kv.PersistedKvMetadata;
import io.kestra.core.repositories.KvMetadataRepositoryInterface;
import io.kestra.core.runners.KVMetadataStateStore;
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

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

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

    @Inject
    private KVMetadataStateStore kvMetadataStateStore;

    @BeforeEach
    public void init() throws IOException {
        storageInterface.delete(MAIN_TENANT, NAMESPACE, toKVUri(NAMESPACE, null));
        List<PersistedKvMetadata> persistedKvMetadata = kvMetadataRepository.find(Pageable.UNPAGED, MAIN_TENANT, Collections.emptyList(), true, true);
        kvMetadataRepository.purge(persistedKvMetadata);
    }

    /**
     * Sorting resolves a {@link KVEntry} field to a repository property; the four whose names
     * differ resolved to nothing, failed the query with a 500 and left the UI with an empty store.
     * Driven off the record components so an added field cannot silently reintroduce the gap.
     */
    @ParameterizedTest
    @MethodSource("kvEntryFields")
    void shouldSortAllKeysByEveryEntryField(String field) throws IOException {
        // Given: two keys in one namespace
        givenTwoKeys();

        // When / Then: every entry field is a whitelisted sort, in both directions
        for (String direction : List.of("asc", "desc")) {
            assertThat(sortedEntries(field, direction))
                .as("sorting by %s:%s should not fail", field, direction)
                .hasSize(2);
        }
    }

    /**
     * A wrong-but-existing column clears the "no 500" bar above: drop the {@code key} mapping and
     * the test still passes, ordering on the uid primary key that happens to share the name. These
     * assertions pin the column each sort actually lands on, on both fields whose name differs from
     * the property they resolve to.
     */
    @Test
    void shouldOrderKeysByTheMappedColumn() throws IOException {
        // Given: a-key at revision 2 and b-key at revision 1, so name order and revision order disagree
        givenTwoKeys();

        // When / Then: `key` orders on the name, not on the uid primary key of the same name
        assertThat(sortedEntries("key", "asc").getFirst().key()).isEqualTo("a-key");
        assertThat(sortedEntries("key", "desc").getFirst().key()).isEqualTo("b-key");

        // And: `revision` orders on `version`, which no name conversion would have reached
        assertThat(sortedEntries("revision", "asc").getFirst().key()).isEqualTo("b-key");
        assertThat(sortedEntries("revision", "desc").getFirst().key()).isEqualTo("a-key");
    }

    @Test
    void shouldRejectASortFieldThatIsNotAnEntryField() {
        // Given / When: a sort on an internal column that was never part of the API contract
        HttpClientResponseException exception = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/main/kv?size=10&page=1&sort=last:asc"),
                Argument.of(PagedResults.class, KVEntry.class)
            )
        );

        // Then: it is answered as an invalid request rather than reaching the query
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    private static Stream<String> kvEntryFields() {
        return Stream.of(KVEntry.class.getRecordComponents()).map(RecordComponent::getName);
    }

    /**
     * Leaves two keys whose name order and revision order disagree: {@code a-key} is written twice so
     * it reaches revision 2 while {@code b-key} stays at 1. The list fetches {@code LATEST} only, so
     * this is still two rows. Revisions rather than timestamps because they are ordered by
     * construction, where two writes can share a clock tick.
     */
    private void givenTwoKeys() throws IOException {
        KVStore kvStore = new InternalKVStore(MAIN_TENANT, TestsUtils.randomNamespace(), storageInterface, kvMetadataStateStore);
        kvStore.put("b-key", new KVValueAndMetadata(new KVMetadata("first", (Instant) null), "b-value"));
        kvStore.put("a-key", new KVValueAndMetadata(new KVMetadata("second", (Instant) null), "a-value"));
        kvStore.put("a-key", new KVValueAndMetadata(new KVMetadata("second", (Instant) null), "a-value-again"));
    }

    @SuppressWarnings("unchecked")
    private List<KVEntry> sortedEntries(String field, String direction) {
        PagedResults<KVEntry> results = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/kv?size=10&page=1&sort=" + field + ":" + direction),
            Argument.of(PagedResults.class, KVEntry.class)
        );

        return results.getResults();
    }

    @SuppressWarnings("unchecked")
    @Test
    void listAllKeys() throws IOException {
        String namespace = TestsUtils.randomNamespace();
        KVStore kvStore = new InternalKVStore(MAIN_TENANT, namespace, storageInterface, kvMetadataStateStore);
        String secondNamespace = TestsUtils.randomNamespace();
        KVStore secondKvStore = new InternalKVStore(MAIN_TENANT, secondNamespace, storageInterface, kvMetadataStateStore);

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
        secondKvStore.put(
            secondNamespaceKey,
            new KVValueAndMetadata(new KVMetadata("anotherNamespaceDescription", Instant.now().plus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.MILLIS)), "another-namespace-value")
        );

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
        assertThat(keyDescriptions).isEqualTo(
            Map.of(
                "shared-key", namespaceParentDescription,
                "parent-key", namespaceParentDescription
            )
        );

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
        Instant beforeInsertion = Instant.now();
        kvStore().put("my-key", new KVValueAndMetadata(new KVMetadata(null, Instant.now().plus(Duration.ofMinutes(5))), value));
        Instant afterInsertion = Instant.now();

        String res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + NAMESPACE + "/kv/my-key"), String.class);
        assertThat(res).contains("\"type\":\"" + expectedType + "\"");
        assertThat(res).contains("\"value\":" + expectedValue);
        assertThat(res).contains("\"revision\":" + 1);
        Pattern updatedDateFinder = Pattern.compile("\"updated\":\\s*\"([^\"]+)\"");
        Matcher matcher = updatedDateFinder.matcher(res);
        matcher.find();
        assertThat(Instant.parse(matcher.group(1))).isBetween(beforeInsertion, afterInsertion);

        beforeInsertion = Instant.now();
        // Test that revision and update date are properly updated
        kvStore().put("my-key", new KVValueAndMetadata(new KVMetadata("some description", Instant.now().plus(Duration.ofMinutes(5))), value));
        afterInsertion = Instant.now();

        res = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + NAMESPACE + "/kv/my-key"), String.class);
        assertThat(res).contains("\"revision\":" + 2);
        matcher = updatedDateFinder.matcher(res);
        matcher.find();
        assertThat(Instant.parse(matcher.group(1))).isBetween(beforeInsertion, afterInsertion);
    }

    @Test
    void getKeyValueNotFound() {
        HttpClientResponseException httpClientResponseException = Assertions
            .assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + NAMESPACE + "/kv/my-key")));
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(httpClientResponseException.getMessage()).isEqualTo("Not Found: No value found for key 'my-key' in namespace '" + NAMESPACE + "'");
    }

    @Test
    void getKeyValueExpired() throws IOException {
        kvStore().put("my-key", new KVValueAndMetadata(new KVMetadata(null, Instant.now().minus(Duration.ofMinutes(5))), "value"));

        HttpClientResponseException httpClientResponseException = Assertions
            .assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + NAMESPACE + "/kv/my-key")));
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
        client.toBlocking()
            .exchange(HttpRequest.PUT("/api/v1/main/namespaces/" + NAMESPACE + "/kv/my-key", value).contentType(mediaType).header("ttl", "PT5M").header("description", myDescription));

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
        return new InternalKVStore(MAIN_TENANT, namespace, storageInterface, kvMetadataStateStore);
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

        HttpClientResponseException httpClientResponseException = Assertions
            .assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/namespaces/" + NAMESPACE + "/kv/bad$key")));
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
        assertThat(httpClientResponseException.getMessage()).isEqualTo(expectedErrorMessage);

        httpClientResponseException = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.PUT("/api/v1/main/namespaces/" + NAMESPACE + "/kv/bad$key", "\"content\"").contentType(MediaType.TEXT_PLAIN))
        );
        assertThat(httpClientResponseException.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
        assertThat(httpClientResponseException.getMessage()).isEqualTo(expectedErrorMessage);

        httpClientResponseException = Assertions
            .assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.DELETE("/api/v1/main/namespaces/" + NAMESPACE + "/kv/bad$key")));
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
