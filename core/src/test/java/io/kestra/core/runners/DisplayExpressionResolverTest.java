package io.kestra.core.runners;

import java.util.List;
import java.util.Map;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.secret.SecretService;
import io.kestra.plugin.core.debug.Return;

import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@MicronautTest
class DisplayExpressionResolverTest {

    @Inject
    private DisplayExpressionResolver resolver;

    @Inject
    private SecretService secretService;

    @MockBean(SecretService.class)
    SecretService mockSecretService() {
        return Mockito.mock(SecretService.class);
    }

    // ---- null / no-expression short-circuits ----

    @Test
    void shouldReturnNullForNullTemplate() {
        assertThat(resolver.resolveForDisplay(null, Map.of())).isNull();
    }

    @Test
    void shouldReturnLiteralWhenNoExpression() {
        assertThat(resolver.resolveForDisplay("hello world", Map.of())).isEqualTo("hello world");
    }

    // ---- vars.* / flow.* resolve ----

    @Test
    void shouldResolveVarsExpression() {
        var vars = Map.of("region", "us-east-1");
        var result = resolver.resolveForDisplay("{{ vars.region }}", Map.of("vars", vars));
        assertThat(result).isEqualTo("us-east-1");
    }

    @Test
    void shouldResolveFlowExpression() {
        var flow = Map.of("id", "my-flow", "namespace", "io.kestra.tests");
        var result = resolver.resolveForDisplay("{{ flow.id }}", Map.of("flow", flow));
        assertThat(result).isEqualTo("my-flow");
    }

    // ---- inputs.* raw pre-exec, resolved post-exec ----

    @Test
    void shouldKeepInputsRawWhenNoExecutionContext() {
        // No "inputs" key in variables → missing → raw
        var result = resolver.resolveForDisplay("{{ inputs.myInput }}", Map.of());
        assertThat(result).isEqualTo("{{ inputs.myInput }}");
    }

    @Test
    void shouldResolveInputsWhenExecutionContextPresent() {
        var inputs = Map.of("myInput", "hello");
        var result = resolver.resolveForDisplay("{{ inputs.myInput }}", Map.of("inputs", inputs));
        assertThat(result).isEqualTo("hello");
    }

    // ---- secret() → masked ----

    @Test
    void shouldMaskSecretFunction() throws Exception {
        var flow = Map.of("namespace", "io.kestra.tests", "id", "test", "tenantId", "");
        var result = resolver.resolveForDisplay("{{ secret('MY_API_KEY') }}", Map.of("flow", flow));
        assertThat(result).isEqualTo("[secret: MY_API_KEY]");

        // The real secret service must never be touched — masking short-circuits before invocation.
        Mockito.verify(secretService, Mockito.never()).findSecret(any(), any(), any());
    }

    // ---- env() → masked ----

    @Test
    void shouldMaskEnvFunction() {
        var flow = Map.of("namespace", "io.kestra.tests", "id", "test");
        var envs = Map.of("HOME", "/home/user");
        var result = resolver.resolveForDisplay("{{ env('HOME') }}", Map.of("flow", flow, "envs", envs));
        assertThat(result).isEqualTo("[env: HOME]");
    }

    // ---- non-deterministic functions stay raw ----

    @Test
    void shouldKeepNowRaw() {
        var result = resolver.resolveForDisplay("{{ now() }}", Map.of());
        assertThat(result).isEqualTo("{{ now() }}");
    }

    @Test
    void shouldKeepUuidRaw() {
        var result = resolver.resolveForDisplay("{{ uuid() }}", Map.of());
        assertThat(result).isEqualTo("{{ uuid() }}");
    }

    // ---- allowlist: only pure, side-effect-free functions resolve ----

    @Test
    void shouldResolveSafeAllowlistedFunction() {
        // fromJson is a pure parsing function on the safe allowlist.
        var result = resolver.resolveForDisplay("{{ fromJson('{\"region\":\"eu\"}').region }}", Map.of());
        assertThat(result).isEqualTo("eu");
    }

    @Test
    void shouldKeepKvRawAsNotAllowlisted() {
        // kv() performs IO and is not on the allowlist → kept raw, never invoked.
        var result = resolver.resolveForDisplay("{{ kv('my_key') }}", Map.of());
        assertThat(result).isEqualTo("{{ kv('my_key') }}");
    }

    @Test
    void shouldKeepFetchContextRawAsNotAllowlisted() {
        // fetchContext() would dump the whole variable context → kept raw, never invoked.
        var result = resolver.resolveForDisplay("{{ fetchContext() }}", Map.of());
        assertThat(result).isEqualTo("{{ fetchContext() }}");
    }

    // ---- mixed-string segment resolution ----

    @Test
    void shouldResolveResolvableSegmentsAndKeepRawOthers() {
        // Given: one resolvable segment and one non-deterministic segment
        var vars = Map.of("region", "us-east-1");
        var variables = Map.of("vars", (Object) vars);

        // When
        var result = resolver.resolveForDisplay("{{ vars.region }}-{{ now() }}", variables);

        // Then: resolvable part resolved, non-deterministic part stays raw
        assertThat(result).isEqualTo("us-east-1-{{ now() }}");
    }

    @Test
    void shouldResolveResolvableAndKeepUnresolvableRaw() {
        var vars = Map.of("env", "prod");
        var variables = Map.of("vars", (Object) vars);

        var result = resolver.resolveForDisplay("{{ vars.env }}-{{ inputs.missing }}", variables);

        assertThat(result).isEqualTo("prod-{{ inputs.missing }}");
    }

    // ---- {% raw %} passthrough ----

    @Test
    void shouldPreserveRawBlocks() {
        var result = resolver.resolveForDisplay("{% raw %}{{ vars.region }}{% endraw %}", Map.of());
        assertThat(result).isEqualTo("{% raw %}{{ vars.region }}{% endraw %}");
    }

    // ---- malformed expression falls back to raw ----

    @Test
    void shouldKeepMalformedExpressionRaw() {
        // A syntactically invalid Pebble expression should not throw but be kept raw
        var result = resolver.resolveForDisplay("{{ ??? }}", Map.of());
        assertThat(result).isEqualTo("{{ ??? }}");
    }

    // ---- resolveProperties walks task POJO ----

    @Test
    void shouldResolveTaskProperties() {
        // Given
        var task = Return.builder()
            .id("test-task")
            .type(Return.class.getName())
            .format(Property.ofExpression("{{ vars.greeting }}-world"))
            .build();
        var vars = Map.of("greeting", "hello");
        var variables = Map.of("vars", (Object) vars);

        // When
        var resolved = resolver.resolveProperties(task, variables);

        // Then: Property serializes as its expression string, which gets resolved
        assertThat(resolved).containsKey("format");
        assertThat(resolved.get("format")).isEqualTo("hello-world");
    }

    @Test
    void shouldMaskSecretsInResolvedProperties() {
        // Given: task with a secret expression in a property
        var task = Return.builder()
            .id("secret-task")
            .type(Return.class.getName())
            .format(Property.ofExpression("{{ secret('DB_PASSWORD') }}"))
            .build();
        var flow = Map.of("namespace", "io.kestra.tests", "id", "test", "tenantId", "");
        var variables = Map.of("flow", (Object) flow);

        // When
        var resolved = resolver.resolveProperties(task, variables);

        // Then: secret is masked, NOT the real password
        assertThat(resolved.get("format")).isEqualTo("[secret: DB_PASSWORD]");
    }
}
