package io.kestra.core.runners;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.secret.SecretService;

import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@MicronautTest
class DisplayExpressionRendererTest {

    @Inject
    private DisplayExpressionRenderer renderer;

    @Inject
    private SecretService secretService;

    @MockBean(SecretService.class)
    SecretService mockSecretService() {
        return Mockito.mock(SecretService.class);
    }

    // ---- render(List, Map): keyed by raw expression ----

    @Test
    void shouldRenderListKeyedByRawExpression() {
        var vars = Map.of("region", "us-east-1");
        var variables = Map.of("vars", (Object) vars);

        var result = renderer.render(List.of("{{ vars.region }}", "{{ now() }}", "literal"), variables);

        assertThat(result).containsEntry("{{ vars.region }}", "us-east-1");
        assertThat(result).containsEntry("{{ now() }}", "{{ now() }}");
        assertThat(result).containsEntry("literal", "literal");
    }

    @Test
    void shouldRenderEmptyMapForNullOrEmptyList() {
        assertThat(renderer.render((List<String>) null, Map.of())).isEmpty();
        assertThat(renderer.render(List.of(), Map.of())).isEmpty();
    }

    @Test
    void shouldIgnoreNullEntries() {
        var result = renderer.render(Arrays.asList("literal", null), Map.of());
        assertThat(result).containsOnlyKeys("literal");
    }

    // ---- null / no-expression short-circuits ----

    @Test
    void shouldReturnNullForNullTemplate() {
        assertThat(renderer.render((String) null, Map.of())).isNull();
    }

    @Test
    void shouldReturnLiteralWhenNoExpression() {
        assertThat(renderer.render("hello world", Map.of())).isEqualTo("hello world");
    }

    // ---- vars.* / flow.* resolve ----

    @Test
    void shouldResolveVarsExpression() {
        var vars = Map.of("region", "us-east-1");
        var result = renderer.render("{{ vars.region }}", Map.of("vars", vars));
        assertThat(result).isEqualTo("us-east-1");
    }

    @Test
    void shouldResolveFlowExpression() {
        var flow = Map.of("id", "my-flow", "namespace", "io.kestra.tests");
        var result = renderer.render("{{ flow.id }}", Map.of("flow", flow));
        assertThat(result).isEqualTo("my-flow");
    }

    // ---- inputs.* raw pre-exec, resolved post-exec ----

    @Test
    void shouldKeepInputsRawWhenNoExecutionContext() {
        var result = renderer.render("{{ inputs.myInput }}", Map.of());
        assertThat(result).isEqualTo("{{ inputs.myInput }}");
    }

    @Test
    void shouldResolveInputsWhenExecutionContextPresent() {
        var inputs = Map.of("myInput", "hello");
        var result = renderer.render("{{ inputs.myInput }}", Map.of("inputs", inputs));
        assertThat(result).isEqualTo("hello");
    }

    // ---- secret() → masked, real service never touched ----

    @Test
    void shouldMaskSecretFunction() throws Exception {
        var flow = Map.of("namespace", "io.kestra.tests", "id", "test", "tenantId", "");
        var result = renderer.render("{{ secret('MY_API_KEY') }}", Map.of("flow", flow));
        assertThat(result).isEqualTo("[secret: MY_API_KEY]");

        Mockito.verify(secretService, Mockito.never()).findSecret(any(), any(), any());
    }

    // ---- env() → kept raw (issue #16874: env vars are not resolved for display) ----

    @Test
    void shouldKeepEnvFunctionRaw() {
        var flow = Map.of("namespace", "io.kestra.tests", "id", "test");
        var envs = Map.of("HOME", "/home/user");
        var result = renderer.render("{{ env('HOME') }}", Map.of("flow", flow, "envs", envs));
        assertThat(result).isEqualTo("{{ env('HOME') }}");
    }

    // ---- non-deterministic functions stay raw ----

    @Test
    void shouldKeepNowRaw() {
        assertThat(renderer.render("{{ now() }}", Map.of())).isEqualTo("{{ now() }}");
    }

    @Test
    void shouldKeepUuidRaw() {
        assertThat(renderer.render("{{ uuid() }}", Map.of())).isEqualTo("{{ uuid() }}");
    }

    // ---- allowlist: only pure, side-effect-free functions resolve ----

    @Test
    void shouldResolveSafeAllowlistedFunction() {
        var result = renderer.render("{{ fromJson('{\"region\":\"eu\"}').region }}", Map.of());
        assertThat(result).isEqualTo("eu");
    }

    @Test
    void shouldKeepKvRawAsNotAllowlisted() {
        var result = renderer.render("{{ kv('my_key') }}", Map.of());
        assertThat(result).isEqualTo("{{ kv('my_key') }}");
    }

    @Test
    void shouldKeepFetchContextRawAsNotAllowlisted() {
        var result = renderer.render("{{ fetchContext() }}", Map.of());
        assertThat(result).isEqualTo("{{ fetchContext() }}");
    }

    // ---- mixed strings: all-or-nothing ----

    @Test
    void shouldResolveWholeTemplateWhenEveryExpressionResolves() {
        var vars = Map.of("region", "us-east-1", "env", "prod");
        var variables = Map.of("vars", (Object) vars);

        var result = renderer.render("{{ vars.region }}-{{ vars.env }}", variables);

        assertThat(result).isEqualTo("us-east-1-prod");
    }

    @Test
    void shouldKeepWholeTemplateRawWhenAFunctionIsUnresolvable() {
        var vars = Map.of("region", "us-east-1");
        var variables = Map.of("vars", (Object) vars);

        // now() is removed from the display engine, so the whole render aborts.
        var result = renderer.render("{{ vars.region }}-{{ now() }}", variables);

        assertThat(result).isEqualTo("{{ vars.region }}-{{ now() }}");
    }

    @Test
    void shouldKeepWholeTemplateRawWhenAVariableIsMissing() {
        var vars = Map.of("env", "prod");
        var variables = Map.of("vars", (Object) vars);

        // inputs.missing is unresolvable under strictVariables, so the whole render aborts.
        var result = renderer.render("{{ vars.env }}-{{ inputs.missing }}", variables);

        assertThat(result).isEqualTo("{{ vars.env }}-{{ inputs.missing }}");
    }

    // ---- {% raw %} blocks are handled natively by Pebble (preserved verbatim, not resolved) ----

    @Test
    void shouldPreserveRawBlocks() {
        var result = renderer.render("{% raw %}{{ vars.region }}{% endraw %}", Map.of());
        assertThat(result).isEqualTo("{% raw %}{{ vars.region }}{% endraw %}");
    }

    @Test
    void shouldPreserveRawBlocksEvenWhenVariableIsResolvable() {
        // The variable is in context but the raw block must prevent resolution.
        var vars = Map.of("region", "us-east-1");
        var result = renderer.render("{% raw %}{{ vars.region }}{% endraw %}", Map.of("vars", vars));
        assertThat(result).isEqualTo("{% raw %}{{ vars.region }}{% endraw %}");
    }

    // ---- malformed expression falls back to raw ----

    @Test
    void shouldKeepMalformedExpressionRaw() {
        var result = renderer.render("{{ ??? }}", Map.of());
        assertThat(result).isEqualTo("{{ ??? }}");
    }
}
