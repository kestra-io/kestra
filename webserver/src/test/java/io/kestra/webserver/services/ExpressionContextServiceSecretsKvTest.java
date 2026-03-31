package io.kestra.webserver.services;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.RunContextCache;
import io.kestra.core.runners.pebble.PebbleExpressionService;
import io.kestra.core.runners.pebble.PebbleFunction;
import io.kestra.core.secret.SecretService;
import io.kestra.core.services.KVStoreService;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.KVEntry;
import io.kestra.core.storages.kv.KVStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpressionContextServiceSecretsKvTest {

    @Mock
    private JsonSchemaGenerator jsonSchemaGenerator;
    @Mock
    private PebbleExpressionService pebbleExpressionService;
    @Mock
    private RunContextCache runContextCache;
    @Mock
    @SuppressWarnings("rawtypes")
    private SecretService secretService;
    @Mock
    private KVStoreService kvStoreService;
    @Mock
    private StorageInterface storageInterface;
    @Mock
    private NamespaceFactory namespaceFactory;

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnSecretsInExpectedFormat() throws Exception {
        // Given
        when(pebbleExpressionService.filters()).thenReturn(List.of());
        when(pebbleExpressionService.functions()).thenReturn(List.of());
        when(secretService.inheritedSecrets(any(), any())).thenReturn(
            Map.of("io.kestra.test", Set.of("DB_PASSWORD", "API_KEY"))
        );
        KVStore kvStore = mock(KVStore.class);
        when(kvStore.list()).thenReturn(List.of());
        when(kvStoreService.get(any(), any())).thenReturn(kvStore);

        ExpressionContextService service = new ExpressionContextService(
            jsonSchemaGenerator, pebbleExpressionService, runContextCache, secretService, kvStoreService, storageInterface, namespaceFactory
        );

        Flow flow = Flow.builder()
            .id("test-flow")
            .namespace("io.kestra.test")
            .tasks(List.of(mock(io.kestra.core.models.tasks.Task.class)))
            .build();

        // When
        Map<String, List<String>> result = service.buildExpressionContext(flow, null);

        // Then
        List<String> secrets = result.get("Secrets");
        assertThat(secrets).hasSize(2);
        assertThat(secrets).contains("secret('API_KEY')", "secret('DB_PASSWORD')");
        // Should be sorted
        assertThat(secrets).isSorted();
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnKvPairsInExpectedFormat() throws Exception {
        // Given
        when(pebbleExpressionService.filters()).thenReturn(List.of());
        when(pebbleExpressionService.functions()).thenReturn(List.of());
        when(secretService.inheritedSecrets(any(), any())).thenReturn(Map.of());

        KVStore kvStore = mock(KVStore.class);
        when(kvStore.list()).thenReturn(List.of(
            new KVEntry("io.kestra.test", "cache_ttl", 1, null, null, null, null),
            new KVEntry("io.kestra.test", "feature_flag_enabled", 1, null, null, null, null)
        ));
        when(kvStoreService.get(any(), any())).thenReturn(kvStore);

        ExpressionContextService service = new ExpressionContextService(
            jsonSchemaGenerator, pebbleExpressionService, runContextCache, secretService, kvStoreService, storageInterface, namespaceFactory
        );

        Flow flow = Flow.builder()
            .id("test-flow")
            .namespace("io.kestra.test")
            .tasks(List.of(mock(io.kestra.core.models.tasks.Task.class)))
            .build();

        // When
        Map<String, List<String>> result = service.buildExpressionContext(flow, null);

        // Then
        List<String> kvPairs = result.get("KV Pairs");
        assertThat(kvPairs).hasSize(2);
        assertThat(kvPairs).contains("kv('cache_ttl')", "kv('feature_flag_enabled')");
        // Should be sorted
        assertThat(kvPairs).isSorted();
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnBothSecretsAndKvPairs() throws Exception {
        // Given
        when(pebbleExpressionService.filters()).thenReturn(List.of("upper", "lower"));
        when(pebbleExpressionService.functions()).thenReturn(List.of(
            new PebbleFunction("now", List.of()),
            new PebbleFunction("secret", List.of(new PebbleFunction.Argument("key", "'MY_SECRET'")))
        ));

        when(secretService.inheritedSecrets(any(), any())).thenReturn(
            Map.of("io.kestra.test", Set.of("SECRET_A", "SECRET_B"))
        );

        KVStore kvStore = mock(KVStore.class);
        when(kvStore.list()).thenReturn(List.of(
            new KVEntry("io.kestra.test", "config_key", 1, null, null, null, null),
            new KVEntry("io.kestra.test", "status_key", 1, null, null, null, null)
        ));
        when(kvStoreService.get(any(), any())).thenReturn(kvStore);

        ExpressionContextService service = new ExpressionContextService(
            jsonSchemaGenerator, pebbleExpressionService, runContextCache, secretService, kvStoreService, storageInterface, namespaceFactory
        );

        Flow flow = Flow.builder()
            .id("test-flow")
            .namespace("io.kestra.test")
            .tasks(List.of(mock(io.kestra.core.models.tasks.Task.class)))
            .build();

        // When
        Map<String, List<String>> result = service.buildExpressionContext(flow, null);

        // Then — secrets
        List<String> secrets = result.get("Secrets");
        assertThat(secrets).containsExactly("secret('SECRET_A')", "secret('SECRET_B')");

        // Then — KV pairs
        List<String> kvPairs = result.get("KV Pairs");
        assertThat(kvPairs).containsExactly("kv('config_key')", "kv('status_key')");

        // Then — filters and functions should also be present
        List<String> filters = result.get("Filters");
        assertThat(filters).contains("| upper", "| lower");

        List<String> other = result.get("Other");
        assertThat(other).contains("now()", "secret('MY_SECRET')");
    }
}
