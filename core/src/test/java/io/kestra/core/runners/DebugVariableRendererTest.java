package io.kestra.core.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.secret.SecretService;
import io.kestra.core.secret.SecretNotFoundException;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class DebugVariableRendererTest {

    @Inject
    private VariableRenderer variableRenderer;
    
    @Inject
    private ApplicationContext applicationContext;

    @MockBean(SecretService.class)
    SecretService testSecretService() {
        return new SecretService() {
            @Override
            public String findSecret(String tenantId, String namespace, String key) throws SecretNotFoundException, IOException {
                return switch (key) {
                    case "MY_SECRET" -> "my-secret-value";
                    case "KEY1" -> "secret-value-1";
                    case "KEY2" -> "secret-value-2";
                    case "API_KEY" -> "api-key-value";
                    default -> throw new SecretNotFoundException("Secret not found: " + key);
                };
            }
        };
    }

    @Test
    void shouldMaskSecretFunctionInDebugMode() throws IllegalVariableEvaluationException {
        // Create a DebugVariableRenderer with secret function masked
        DebugVariableRenderer debugRenderer = new DebugVariableRenderer(
            variableRenderer,
            applicationContext,
            null,
            List.of("secret")
        );

        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest")
        );

        String result = debugRenderer.render("{{ secret('MY_SECRET') }}", context);
        assertThat(result).isEqualTo("******");
    }

    @Test
    void shouldMaskMultipleSecretFunctions() throws IllegalVariableEvaluationException {
        DebugVariableRenderer debugRenderer = new DebugVariableRenderer(
            variableRenderer,
            applicationContext,
            null,
            List.of("secret")
        );

        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest")
        );

        String result = debugRenderer.render("Secrets: {{ secret('KEY1') }} and {{ secret('KEY2') }}", context);
        assertThat(result).isEqualTo("Secrets: ****** and ******");
    }

    @Test
    void shouldNotMaskNonSecretFunctions() throws IllegalVariableEvaluationException {
        DebugVariableRenderer debugRenderer = new DebugVariableRenderer(
            variableRenderer,
            applicationContext,
            null,
            List.of("secret")
        );

        Map<String, Object> context = Map.of(
            "user", "testuser"
        );

        String result = debugRenderer.render("User: {{ user }}", context);
        assertThat(result).isEqualTo("User: testuser");
    }

    @Test
    void shouldMaskOnlySpecifiedFunctions() throws IllegalVariableEvaluationException {
        // Only mask 'secret' function, not 'other' function
        DebugVariableRenderer debugRenderer = new DebugVariableRenderer(
            variableRenderer,
            applicationContext,
            null,
            List.of("secret")
        );

        Map<String, Object> context = Map.of(
            "flow", Map.of("namespace", "io.kestra.unittest"),
            "user", "testuser"
        );

        String result = debugRenderer.render("User: {{ user }}, Secret: {{ secret('MY_SECRET') }}", context);
        assertThat(result).isEqualTo("User: testuser, Secret: ******");
    }
}