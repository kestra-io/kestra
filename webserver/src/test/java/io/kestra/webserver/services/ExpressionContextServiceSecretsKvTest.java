package io.kestra.webserver.services;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.services.ExpressionCategory;
import io.kestra.core.services.ExpressionContext;
import io.kestra.core.services.ExpressionContextService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void shouldForwardTenantIdToSecretAndKvServices() throws Exception {
        // Given
        String tenantId = "tenant-42";
        when(pebbleExpressionService.filters()).thenReturn(List.of());
        when(pebbleExpressionService.functions()).thenReturn(List.of());
        when(secretService.ownAndInheritedSecrets(eq(tenantId), any())).thenReturn(
            Map.of("io.kestra.test", Set.of("TENANT_SECRET"))
        );
        KVStore kvStore = mock(KVStore.class);
        when(kvStore.list()).thenReturn(List.of(
            new KVEntry("io.kestra.test", "tenant_key", 1, null, null, null, null)
        ));
        when(kvStoreService.get(eq(tenantId), any())).thenReturn(kvStore);

        ExpressionContextService service = new ExpressionContextService(
            jsonSchemaGenerator, pebbleExpressionService, runContextCache, secretService, kvStoreService, storageInterface, namespaceFactory
        );

        // Simulate what AiService.buildPebbleExpressions does after parsing YAML with no tenantId:
        //   flow = JacksonMapper.ofYaml().readValue(flowYaml, Flow.class).toBuilder().tenantId(tenantId).build()
        Flow flow = Flow.builder()
            .id("test-flow")
            .namespace("io.kestra.test")
            .tasks(List.of())
            .tenantId(tenantId)  // injected after YAML parse
            .build();

        // When
        ExpressionContext result = service.buildExpressionContext(flow, null);

        // Then — downstream services receive the correct tenantId, not null
        verify(secretService).ownAndInheritedSecrets(eq(tenantId), any());
        verify(kvStoreService).get(eq(tenantId), any());

        List<String> secrets = result.categories().get(ExpressionCategory.SECRETS);
        assertThat(secrets).contains("secret('TENANT_SECRET')");

        List<String> kvPairs = result.categories().get(ExpressionCategory.KV_PAIRS);
        assertThat(kvPairs).contains("kv('tenant_key')");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnSecretsInExpectedFormat() throws Exception {
        // Given
        when(pebbleExpressionService.filters()).thenReturn(List.of());
        when(pebbleExpressionService.functions()).thenReturn(List.of());
        when(secretService.ownAndInheritedSecrets(any(), any())).thenReturn(
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
        ExpressionContext result = service.buildExpressionContext(flow, null);

        // Then
        List<String> secrets = result.categories().get(ExpressionCategory.SECRETS);
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
        when(secretService.ownAndInheritedSecrets(any(), any())).thenReturn(Map.of());

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
        ExpressionContext result = service.buildExpressionContext(flow, null);

        // Then
        List<String> kvPairs = result.categories().get(ExpressionCategory.KV_PAIRS);
        assertThat(kvPairs).hasSize(2);
        assertThat(kvPairs).contains("kv('cache_ttl')", "kv('feature_flag_enabled')");
        // Should be sorted
        assertThat(kvPairs).isSorted();
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldIncludeOwnNamespaceSecrets() throws Exception {
        // Given — own namespace has a secret, parent namespace has another
        when(pebbleExpressionService.filters()).thenReturn(List.of());
        when(pebbleExpressionService.functions()).thenReturn(List.of());
        when(secretService.ownAndInheritedSecrets(any(), any())).thenReturn(
            Map.of(
                "io.kestra", Set.of("PARENT_SECRET"),
                "io.kestra.test", Set.of("OWN_SECRET")
            )
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
            .tasks(List.of())
            .build();

        // When
        ExpressionContext result = service.buildExpressionContext(flow, null);

        // Then — both own and parent secrets appear in autocomplete
        List<String> secrets = result.categories().get(ExpressionCategory.SECRETS);
        assertThat(secrets).contains("secret('OWN_SECRET')", "secret('PARENT_SECRET')");
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

        when(secretService.ownAndInheritedSecrets(any(), any())).thenReturn(
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
        ExpressionContext result = service.buildExpressionContext(flow, null);

        // Then — secrets
        List<String> secrets = result.categories().get(ExpressionCategory.SECRETS);
        assertThat(secrets).containsExactly("secret('SECRET_A')", "secret('SECRET_B')");

        // Then — KV pairs
        List<String> kvPairs = result.categories().get(ExpressionCategory.KV_PAIRS);
        assertThat(kvPairs).containsExactly("kv('config_key')", "kv('status_key')");

        // Then — filters are plain names (no "| " prefix); functions are call signatures
        List<String> filters = result.categories().get(ExpressionCategory.FILTERS);
        assertThat(filters).contains("upper", "lower");

        List<String> functions = result.categories().get(ExpressionCategory.FUNCTIONS);
        assertThat(functions).contains("now()", "secret(key='MY_SECRET')");
    }

    @Test
    void shouldSkipExcludedCategoriesAndNotCallBackends() throws Exception {
        // Given
        when(pebbleExpressionService.filters()).thenReturn(List.of());
        when(pebbleExpressionService.functions()).thenReturn(List.of());

        ExpressionContextService service = new ExpressionContextService(
            jsonSchemaGenerator, pebbleExpressionService, runContextCache, secretService, kvStoreService, storageInterface, namespaceFactory
        );

        Flow flow = Flow.builder()
            .id("test-flow")
            .namespace("io.kestra.test")
            .tasks(List.of())
            .build();

        // When — exclude all three sensitive categories
        ExpressionContext result = service.buildExpressionContext(
            flow, null,
            Set.of(ExpressionCategory.SECRETS, ExpressionCategory.KV_PAIRS, ExpressionCategory.NAMESPACE_FILES)
        );

        // Then — backends never queried
        verify(secretService, never()).ownAndInheritedSecrets(any(), any());
        verify(kvStoreService, never()).get(any(), any());
        verify(namespaceFactory, never()).of(any(), any(), any());

        // And — excluded categories absent from result
        assertThat(result.categories()).doesNotContainKeys(
            ExpressionCategory.SECRETS, ExpressionCategory.KV_PAIRS, ExpressionCategory.NAMESPACE_FILES
        );
    }
}
