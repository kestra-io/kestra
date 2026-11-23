package io.kestra.core.runners;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.secret.SecretNotFoundException;
import io.kestra.core.secret.SecretService;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.kv.InternalKVStore;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SecureVariableRendererFactory.
 * 
 * This class tests the factory's ability to create debug renderers that:
 * - Properly mask secret functions
 * - Maintain security by preventing secret value leakage
 * - Delegate to the base renderer for non-secret operations
 * - Handle errors appropriately
 */
@KestraTest
class SecureVariableRendererFactoryTest {

    @Inject
    private SecureVariableRendererFactory secureVariableRendererFactory;

    @Inject
    private VariableRenderer renderer;

    @Inject
    private KVMetadataStateStore kvMetadataStateStore;

    @Inject
    private StorageInterface storageInterface;

    @MockBean(SecretService.class)
    SecretService testSecretService() {
        return new SecretService() {
            @Override
            public String findSecret(String tenantId, String namespace, String key) throws SecretNotFoundException, IOException {
                return switch (key) {
                    case "MY_SECRET" -> "my-secret-value-12345";
                    case "API_KEY" -> "api-key-value-67890";
                    case "DB_PASSWORD" -> "db-password-secret";
                    case "TOKEN" -> "token-value-abc123";
                    case "KEY1" -> "secret-value-1";
                    case "KEY2" -> "secret-value-2";
                    case "JSON_SECRET" -> "{\"api_key\": \"secret123\", \"token\": \"token456\"}";
                    default -> throw new SecretNotFoundException("Secret not found: " + key);
                };
            }
        };
    }

    @Test
    void shouldCreateDebugRenderer() {
        // When
        VariableRenderer debugRenderer = secureVariableRendererFactory.createOrGet();

        // Then
        assertThat(debugRenderer).isNotNull();
    }

    @Test
    void shouldCreateDebugRendererThatIsNotSameAsBaseRenderer() {
        // When
        VariableRenderer debugRenderer = secureVariableRendererFactory.createOrGet();

        // Then
        assertThat(debugRenderer).isNotSameAs(renderer);
    }

    @Test
    void shouldCreateDebugRendererThatMasksSecrets() throws IllegalVariableEvaluationException {
        // Given
        VariableRenderer debugRenderer = secureVariableRendererFactory.createOrGet();
        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest")
        );

        // When
        String result = debugRenderer.render("{{ secret('MY_SECRET') }}", context);

        // Then
        assertThat(result).isEqualTo("******");
        assertThat(result).doesNotContain("my-secret-value-12345");
    }

    @Test
    void shouldCreateDebugRendererThatMasksMultipleSecrets() throws IllegalVariableEvaluationException {
        // Given
        VariableRenderer debugRenderer = secureVariableRendererFactory.createOrGet();
        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest")
        );

        // When
        String result = debugRenderer.render(
            "API: {{ secret('API_KEY') }}, DB: {{ secret('DB_PASSWORD') }}, Token: {{ secret('TOKEN') }}",
            context
        );

        // Then
        assertThat(result).isEqualTo("API: ******, DB: ******, Token: ******");
        assertThat(result).doesNotContain("api-key-value-67890");
        assertThat(result).doesNotContain("db-password-secret");
        assertThat(result).doesNotContain("token-value-abc123");
    }

    @Test
    void shouldCreateDebugRendererThatDoesNotMaskNonSecretVariables() throws IllegalVariableEvaluationException {
        // Given
        VariableRenderer debugRenderer = secureVariableRendererFactory.createOrGet();
        Map<String, Object> context = Map.of(
            "username", "testuser",
            "email", "test@example.com",
            "count", 42
        );

        // When
        String result = debugRenderer.render(
            "User: {{ username }}, Email: {{ email }}, Count: {{ count }}",
            context
        );

        // Then
        assertThat(result).isEqualTo("User: testuser, Email: test@example.com, Count: 42");
    }

    @Test
    void shouldCreateDebugRendererThatMasksOnlySecretFunctions() throws IllegalVariableEvaluationException {
        // Given
        VariableRenderer debugRenderer = secureVariableRendererFactory.createOrGet();
        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest"),
            "username", "testuser",
            "environment", "production"
        );

        // When
        String result = debugRenderer.render(
            "User: {{ username }}, Env: {{ environment }}, Secret: {{ secret('MY_SECRET') }}",
            context
        );

        // Then
        assertThat(result).isEqualTo("User: testuser, Env: production, Secret: ******");
        assertThat(result).contains("testuser");
        assertThat(result).contains("production");
        assertThat(result).doesNotContain("my-secret-value-12345");
    }

    /**
     * Regression test for issue #10244 — the masked-functions list on this read-only renderer covered
     * only secret(), so a caller-supplied expression like {@code {{ kv('KEY') }}} resolved to the
     * plaintext K/V value with no KVSTORE permission check anywhere on the call path.
     */
    @Test
    void shouldCreateDebugRendererThatMasksKv() throws IllegalVariableEvaluationException, IOException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        KVStore kv = new InternalKVStore(tenant, "io.kestra.unittest", storageInterface, kvMetadataStateStore);
        kv.put("SOME_KEY", new KVValueAndMetadata(null, "super-secret"));

        VariableRenderer debugRenderer = secureVariableRendererFactory.createOrGet();
        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest", "tenantId", tenant)
        );

        // When
        String result = debugRenderer.render("{{ kv('SOME_KEY') }}", context);

        // Then
        assertThat(result).isEqualTo("******");
        assertThat(result).doesNotContain("super-secret");
    }

    @Test
    void shouldCreateDebugRendererThatHandlesMissingSecrets() {
        // Given
        VariableRenderer debugRenderer = secureVariableRendererFactory.createOrGet();
        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest")
        );

        // When/Then
        assertThatThrownBy(() -> debugRenderer.render("{{ secret('NON_EXISTENT_SECRET') }}", context))
            .isInstanceOf(IllegalVariableEvaluationException.class)
            .hasMessageContaining("Secret not found: NON_EXISTENT_SECRET");
    }

    @Test
    void shouldCreateDebugRendererThatMasksSecretsInComplexExpressions() throws IllegalVariableEvaluationException {
        // Given
        VariableRenderer debugRenderer = secureVariableRendererFactory.createOrGet();
        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest")
        );

        // When
        String result = debugRenderer.render(
            "{{ 'API Key: ' ~ secret('API_KEY') }}",
            context
        );

        // Then
        assertThat(result).isEqualTo("API Key: ******");
        assertThat(result).doesNotContain("api-key-value-67890");
    }

    @Test
    void shouldCreateDebugRendererThatMasksSecretsInConditionals() throws IllegalVariableEvaluationException {
        // Given
        VariableRenderer debugRenderer = secureVariableRendererFactory.createOrGet();
        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest")
        );

        // When
        String result = debugRenderer.render(
            "{{ secret('MY_SECRET') is defined ? 'Secret exists' : 'No secret' }}",
            context
        );

        // Then
        assertThat(result).isEqualTo("Secret exists");
        assertThat(result).doesNotContain("my-secret-value-12345");
    }

    @Test
    void shouldCreateDebugRendererThatMasksSecretsWithSubkeys() throws IllegalVariableEvaluationException {
        // Given
        VariableRenderer debugRenderer = secureVariableRendererFactory.createOrGet();
        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest")
        );

        // When
        String result = debugRenderer.render(
            "{{ secret('JSON_SECRET', subkey='api_key') }}",
            context
        );

        // Then
        assertThat(result).isEqualTo("******");
        assertThat(result).doesNotContain("secret123");
    }

    @Test
    void shouldCreateDebugRendererThatHandlesEmptyContext() throws IllegalVariableEvaluationException {
        // Given
        VariableRenderer debugRenderer = secureVariableRendererFactory.createOrGet();
        Map<String, Object> emptyContext = Map.of();

        // When
        String result = debugRenderer.render("Hello World", emptyContext);

        // Then
        assertThat(result).isEqualTo("Hello World");
    }

    @Test
    void shouldCreateDebugRendererThatHandlesNullValues() throws IllegalVariableEvaluationException {
        // Given
        VariableRenderer debugRenderer = secureVariableRendererFactory.createOrGet();
        Map<String, Object> context = Map.of(
            "value", "test"
        );

        // When
        String result = debugRenderer.render("{{ value }}", context);

        // Then
        assertThat(result).isEqualTo("test");
    }

    @Test
    void shouldCreateDebugRendererThatMasksSecretsInNestedRender() throws IllegalVariableEvaluationException {
        // Given
        VariableRenderer debugRenderer = secureVariableRendererFactory.createOrGet();
        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest")
        );

        // When - Using concatenation to avoid immediate evaluation
        String result = debugRenderer.render(
            "{{ render('{{s'~'ecret(\"MY_SECRET\")}}') }}",
            context
        );

        // Then
        assertThat(result).isEqualTo("******");
        assertThat(result).doesNotContain("my-secret-value-12345");
    }
}
