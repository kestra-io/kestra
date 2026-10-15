package io.kestra.core.models.flows.check;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class CheckTest {

    @Test
    void shouldReturnCreateExecutionGivenNullList() {
        // Given
        List<Check> checks = null;

        // When
        Check.Behavior result = Check.resolveBehavior(checks);

        // Then
        assertThat(result).isEqualTo(Check.Behavior.CREATE_EXECUTION);
    }

    @Test
    void shouldReturnCreateExecutionGivenEmptyList() {
        // Given
        List<Check> checks = List.of();

        // When
        Check.Behavior result = Check.resolveBehavior(checks);

        // Then
        assertThat(result).isEqualTo(Check.Behavior.CREATE_EXECUTION);
    }

    @Test
    void shouldReturnCreateExecutionGivenOnlyCreateExecutionChecks() {
        // Given
        List<Check> checks = List.of(
            Check.builder().behavior(Check.Behavior.CREATE_EXECUTION).build(),
            Check.builder().behavior(Check.Behavior.CREATE_EXECUTION).build()
        );

        // When
        Check.Behavior result = Check.resolveBehavior(checks);

        // Then
        assertThat(result).isEqualTo(Check.Behavior.CREATE_EXECUTION);
    }

    @Test
    void shouldReturnFailExecutionGivenFailAndCreateChecks() {
        // Given
        List<Check> checks = List.of(
            Check.builder().behavior(Check.Behavior.CREATE_EXECUTION).build(),
            Check.builder().behavior(Check.Behavior.FAIL_EXECUTION).build()
        );

        // When
        Check.Behavior result = Check.resolveBehavior(checks);

        // Then
        assertThat(result).isEqualTo(Check.Behavior.FAIL_EXECUTION);
    }

    @Test
    void shouldReturnBlockExecutionGivenMixedBehaviors() {
        // Given
        List<Check> checks = List.of(
            Check.builder().behavior(Check.Behavior.CREATE_EXECUTION).build(),
            Check.builder().behavior(Check.Behavior.FAIL_EXECUTION).build(),
            Check.builder().behavior(Check.Behavior.BLOCK_EXECUTION).build()
        );

        // When
        Check.Behavior result = Check.resolveBehavior(checks);

        // Then
        assertThat(result).isEqualTo(Check.Behavior.BLOCK_EXECUTION);
    }

    @Test
    void shouldIgnoreNullBehaviorsGivenMixedValues() {
        // Given
        List<Check> checks = List.of(
            Check.builder().behavior(null).build(),
            Check.builder().behavior(Check.Behavior.CREATE_EXECUTION).build()
        );

        // When
        Check.Behavior result = Check.resolveBehavior(checks);

        // Then
        assertThat(result).isEqualTo(Check.Behavior.CREATE_EXECUTION);
    }

    private static final ObjectMapper YAML_MAPPER = JacksonMapper.ofYaml();

    @Test
    void shouldReadPassIfAsPrimaryName() throws Exception {
        Check check = YAML_MAPPER.readValue("passIf: \"{{ true }}\"\nmessage: ok\n", Check.class);

        assertThat(check.getPassIf()).isEqualTo("{{ true }}");
    }

    @Test
    void shouldReadWhenAsDeprecatedAlias() throws Exception {
        Check check = YAML_MAPPER.readValue("when: \"{{ true }}\"\nmessage: ok\n", Check.class);

        assertThat(check.getPassIf()).isEqualTo("{{ true }}");
    }

    @Test
    void shouldReadConditionAsDeprecatedAlias() throws Exception {
        Check check = YAML_MAPPER.readValue("condition: \"{{ true }}\"\nmessage: ok\n", Check.class);

        assertThat(check.getPassIf()).isEqualTo("{{ true }}");
    }

    @Test
    void shouldRejectCheckSettingMoreThanOneConditionName() {
        assertThatThrownBy(() -> YAML_MAPPER.readValue("passIf: \"{{ true }}\"\nwhen: \"{{ true }}\"\nmessage: ok\n", Check.class))
            .rootCause()
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("only one of `passIf`, `when` and `condition`");
    }

    @Test
    void shouldSerializeConditionAsPassIf() throws Exception {
        Check check = YAML_MAPPER.readValue("when: \"{{ true }}\"\nmessage: ok\n", Check.class);

        String yaml = YAML_MAPPER.writeValueAsString(check);

        assertThat(yaml).contains("passIf:");
        assertThat(yaml).doesNotContain("when:");
        assertThat(yaml).doesNotContain("condition:");
    }
}
