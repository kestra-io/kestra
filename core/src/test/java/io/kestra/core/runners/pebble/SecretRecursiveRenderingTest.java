package io.kestra.core.runners.pebble;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;
import io.kestra.core.secret.SecretNotFoundException;
import io.kestra.core.secret.SecretService;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
@Property(name = "kestra.variables.recursive-rendering", value = "true")
class SecretRecursiveRenderingTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void shouldRenderSecretContainingUnclosedPebbleCommentSyntax() throws IllegalVariableEvaluationException {
        Map<String, Object> context = Map.of("flow", Map.of("namespace", "io.kestra.unittest"));

        String rendered = variableRenderer.render(" {{ secret('pebble-comment-secret') }} ", context);

        assertThat(rendered).isEqualTo(" test_secret_with_pebble_comment_{#asd ");
    }

    @Test
    void shouldRenderSecretContainingPebbleExpressionSyntax() throws IllegalVariableEvaluationException {
        Map<String, Object> context = Map.of("flow", Map.of("namespace", "io.kestra.unittest"));

        String rendered = variableRenderer.render("{{ secret('pebble-expression-secret') }}", context);

        assertThat(rendered).isEqualTo("value_{{ not_a_variable }}_end");
    }

    @Test
    void shouldRenderFullSecretMetadataContainingPebbleSyntax() throws IllegalVariableEvaluationException {
        Map<String, Object> context = Map.of("flow", Map.of("namespace", "io.kestra.unittest"));

        assertThat(variableRenderer.render("{{ secret('pebble-comment-secret', full=true).value }}", context))
            .isEqualTo("test_secret_with_pebble_comment_{#asd");
    }

    @MockBean(SecretService.class)
    public static class TestSecretService extends SecretService {
        private static final Map<String, String> SECRETS = Map.of(
            "pebble-comment-secret", "test_secret_with_pebble_comment_{#asd",
            "pebble-expression-secret", "value_{{ not_a_variable }}_end"
        );

        @Override
        public String findSecret(String tenantId, String namespace, String key) throws SecretNotFoundException, IOException {
            if (SECRETS.containsKey(key)) {
                return SECRETS.get(key);
            }
            return super.findSecret(tenantId, namespace, key);
        }

        @Override
        public io.kestra.core.secret.SecretObject findSecretObject(String tenantId, String namespace, String key) throws SecretNotFoundException, IOException {
            if (SECRETS.containsKey(key)) {
                return new io.kestra.core.secret.SecretObject(SECRETS.get(key), Map.of());
            }
            return super.findSecretObject(tenantId, namespace, key);
        }
    }
}
