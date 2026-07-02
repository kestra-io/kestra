package io.kestra.core.runners.pebble.functions;

import java.util.HashMap;
import java.util.Map;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.VariableRenderer;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.kestra.core.runners.pebble.functions.FunctionTestUtils.getVariables;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class EnvFunctionTest {
    @Inject
    VariableRenderer variableRenderer;

    @Test
    void shouldGetValueFromEnvironmentVariable() throws IllegalVariableEvaluationException {
        Map<String, Object> variables = variablesWithEnvs(Map.of("DATABASE_HOST", "postgres"));

        String rendered = variableRenderer.render("{{ env('DATABASE_HOST') }}", variables);

        assertThat(rendered).isEqualTo("postgres");
    }

    @Test
    void shouldGetValueFromDynamicEnvironmentVariableName() throws IllegalVariableEvaluationException {
        Map<String, Object> variables = variablesWithEnvs(Map.of("DATABASE_HOST", "postgres"));
        variables.put("envName", "DATABASE_HOST");

        String rendered = variableRenderer.render("{{ env(envName) }}", variables);

        assertThat(rendered).isEqualTo("postgres");
    }

    @Test
    void shouldReturnDefaultForMissingEnvironmentVariable() throws IllegalVariableEvaluationException {
        Map<String, Object> variables = variablesWithEnvs(Map.of("DATABASE_HOST", "postgres"));

        String rendered = variableRenderer.render("{{ env('DATABASE_PORT', '5432') }}", variables);

        assertThat(rendered).isEqualTo("5432");
    }

    @Test
    void shouldReturnDefaultForEmptyEnvironmentVariable() throws IllegalVariableEvaluationException {
        Map<String, Object> variables = variablesWithEnvs(Map.of("DATABASE_HOST", ""));

        String rendered = variableRenderer.render("{{ env('DATABASE_HOST', 'localhost') }}", variables);

        assertThat(rendered).isEqualTo("localhost");
    }

    @Test
    void shouldReturnEmptyForMissingEnvironmentVariableWithoutDefault() throws IllegalVariableEvaluationException {
        Map<String, Object> variables = variablesWithEnvs(Map.of());

        String rendered = variableRenderer.render("{{ env('DATABASE_HOST') }}", variables);

        assertThat(rendered).isEmpty();
    }

    @Test
    void shouldFailWithoutNameArgument() {
        Map<String, Object> variables = variablesWithEnvs(Map.of());

        IllegalVariableEvaluationException exception = Assertions.assertThrows(
            IllegalVariableEvaluationException.class,
            () -> variableRenderer.render("{{ env() }}", variables)
        );

        assertThat(exception.getMessage())
            .isEqualTo("io.pebbletemplates.pebble.error.PebbleException: The 'env' function expects an argument 'name'. ({{ env() }}:1)");
    }

    private Map<String, Object> variablesWithEnvs(Map<String, String> envs) {
        Map<String, Object> variables = new HashMap<>(getVariables());
        variables.put("envs", envs);
        return variables;
    }
}
