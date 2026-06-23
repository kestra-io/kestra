package io.kestra.core.runners.pebble;

import java.util.List;

import io.kestra.core.junit.annotations.KestraTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class PebbleExpressionServiceTest {

    @Inject
    PebbleExpressionService pebbleExpressionService;

    private PebbleFunction findFunction(String name) {
        return pebbleExpressionService.functions().stream()
            .filter(f -> f.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Function not found: " + name));
    }

    @Test
    void filtersShouldBeSorted() {
        List<String> filters = pebbleExpressionService.filters();

        assertThat(filters).isNotEmpty();
        assertThat(filters).isSorted();
        assertThat(filters).contains("jq", "upper", "lower", "date");
    }

    @Test
    void functionsShouldBeSortedByName() {
        List<PebbleFunction> functions = pebbleExpressionService.functions();

        assertThat(functions).isNotEmpty();
        assertThat(functions).extracting(PebbleFunction::name).isSorted();
    }

    @Test
    void functionsShouldContainKestraFunctionsWithDefaults() {
        // secret function
        PebbleFunction secret = findFunction("secret");
        assertThat(secret.arguments()).extracting(PebbleFunction.Argument::name)
            .containsExactly("key", "namespace", "subkey", "full");
        assertThat(secret.arguments().get(0).defaultValue()).isEqualTo("'MY_SECRET'");
        assertThat(secret.arguments().get(1).defaultValue()).isEqualTo("flow.namespace");
        assertThat(secret.arguments().get(2).defaultValue()).isNull();
        assertThat(secret.arguments().get(3).defaultValue()).isNull();

        // kv function
        PebbleFunction kv = findFunction("kv");
        assertThat(kv.arguments()).extracting(PebbleFunction.Argument::name)
            .containsExactly("key", "namespace", "errorOnMissing");
        assertThat(kv.arguments().get(0).defaultValue()).isEqualTo("'my_key'");
        assertThat(kv.arguments().get(1).defaultValue()).isEqualTo("flow.namespace");
        assertThat(kv.arguments().get(2).defaultValue()).isNull();

        // env function
        PebbleFunction env = findFunction("env");
        assertThat(env.arguments()).extracting(PebbleFunction.Argument::name)
            .containsExactly("name", "default");
        assertThat(env.arguments().get(0).defaultValue()).isEqualTo("'ENV_NAME'");
        assertThat(env.arguments().get(1).defaultValue()).isNull();

        // randomInt function
        PebbleFunction randomInt = findFunction("randomInt");
        assertThat(randomInt.arguments()).extracting(PebbleFunction.Argument::name)
            .containsExactly("lower", "upper");
        assertThat(randomInt.arguments().get(0).defaultValue()).isEqualTo("0");
        assertThat(randomInt.arguments().get(1).defaultValue()).isEqualTo("10");

        // http function — verify argument order
        PebbleFunction http = findFunction("http");
        assertThat(http.arguments()).extracting(PebbleFunction.Argument::name)
            .containsExactly("uri", "method", "query", "body", "contentType", "headers", "options", "accept");
        assertThat(http.arguments().get(0).defaultValue()).isEqualTo("'https://example.com'");
        assertThat(http.arguments().get(1).defaultValue()).isEqualTo("'GET'");
        assertThat(http.arguments().get(2).defaultValue()).isNull();
    }

    @Test
    void functionsShouldContainNoArgFunctions() {
        PebbleFunction uuid = findFunction("uuid");
        assertThat(uuid.arguments()).isEmpty();

        // now has args from AbstractDate but all with null defaults
        PebbleFunction now = findFunction("now");
        assertThat(now.arguments()).extracting(PebbleFunction.Argument::name)
            .containsExactly("format", "timeZone", "existingFormat", "locale");
        assertThat(now.arguments()).extracting(PebbleFunction.Argument::defaultValue)
            .containsOnlyNulls();
    }

    @Test
    void functionsShouldContainCorePebbleFunctions() {
        List<String> names = pebbleExpressionService.functions().stream()
            .map(PebbleFunction::name).toList();

        assertThat(names).contains("max", "min");
    }

    @Test
    void argumentOrderShouldBePreserved() {
        assertThat(findFunction("encrypt").arguments()).extracting(PebbleFunction.Argument::name)
            .containsExactly("key", "plaintext");
        assertThat(findFunction("decrypt").arguments()).extracting(PebbleFunction.Argument::name)
            .containsExactly("key", "encrypted");
        assertThat(findFunction("render").arguments()).extracting(PebbleFunction.Argument::name)
            .containsExactly("toRender", "recursive");
        assertThat(findFunction("read").arguments()).extracting(PebbleFunction.Argument::name)
            .containsExactly("path", "namespace", "revision");
    }
}
