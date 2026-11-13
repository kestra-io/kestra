package io.kestra.core.models.flows.check;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.serializers.JacksonMapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @SuppressWarnings("deprecation")
    void shouldResolvePassIfFromDeprecatedAliases() {
        Check passIf = Check.builder().passIf("{{ passIf }}").message("m").build();
        Check when = Check.builder().when("{{ when }}").message("m").build();
        Check condition = Check.builder().condition("{{ condition }}").message("m").build();

        assertThat(passIf.getPassIf()).isEqualTo("{{ passIf }}");
        assertThat(when.getPassIf()).isEqualTo("{{ when }}");
        assertThat(when.getWhen()).isEqualTo("{{ when }}");
        assertThat(condition.getPassIf()).isEqualTo("{{ condition }}");
        assertThat(condition.getCondition()).isEqualTo("{{ condition }}");
    }

    @Test
    @SuppressWarnings("deprecation")
    void shouldRejectConflictingNamesWhenSettingValues() {
        Check check = new Check();
        check.setPassIf("{{ true }}");

        assertThatThrownBy(() -> check.setWhen("{{ false }}"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("A check must set only one of 'passIf', 'when' and 'condition'. It sets both 'passIf' and 'when'.");
    }

    @Test
    @SuppressWarnings("deprecation")
    void shouldIgnoreNullAliasWhenPassIfIsSet() {
        Check check = new Check();
        check.setPassIf("{{ true }}");
        check.setWhen(null);
        check.setCondition(null);

        assertThat(check.getPassIf()).isEqualTo("{{ true }}");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        """
        passIf: "{{ true }}"
        when: "{{ false }}"
        message: conflicting
        """,
        """
        passIf: "{{ true }}"
        condition: "{{ false }}"
        message: conflicting
        """,
        """
        when: "{{ false }}"
        condition: "{{ false }}"
        message: conflicting
        """,
        """
        passIf: "{{ true }}"
        when: "{{ false }}"
        condition: "{{ false }}"
        message: conflicting
        """
    })
    void shouldRejectEveryConflictingNameCombinationWhenParsing(String yaml) {
        assertThatThrownBy(() -> JacksonMapper.ofYaml().readValue(yaml, Check.class))
            .hasStackTraceContaining("A check must set only one of 'passIf', 'when' and 'condition'.");
    }

    @Test
    void shouldSerializeAliasAsPassIf() throws Exception {
        Check check = JacksonMapper.ofYaml().readValue("""
            when: "{{ true }}"
            message: ok
            """, Check.class);

        String json = JacksonMapper.ofJson().writeValueAsString(check);

        assertThat(json).contains("\"passIf\":\"{{ true }}\"");
        assertThat(json).doesNotContain("\"when\"", "\"condition\"", "singleConditionName");
    }

    @Test
    void shouldRejectConflictingNamesWhenValidatingModel() {
        Check check = Check.builder().passIf("{{ true }}").condition("{{ false }}").message("conflicting").build();

        assertThat(validationViolations(check))
            .singleElement()
            .satisfies(violation -> assertThat(violation.getMessage())
                .isEqualTo("A check must set only one of 'passIf', 'when' and 'condition'."));
    }

    @Test
    void shouldRejectCheckWithoutConditionNameWhenValidating() {
        Check check = Check.builder().message("missing condition").build();

        assertThat(validationViolations(check))
            .singleElement()
            .satisfies(violation -> assertThat(violation.getMessage())
                .isEqualTo("must not be null"));
    }

    @Test
    void shouldRejectEmptyConditionWhenValidating() {
        Check check = Check.builder().passIf("").message("empty condition").build();

        assertThat(validationViolations(check))
            .singleElement()
            .satisfies(violation -> assertThat(violation.getMessage())
                .isEqualTo("must not be empty"));
    }

    private static Set<ConstraintViolation<Check>> validationViolations(Check check) {
        try (ValidatorFactory factory = Validation
            .byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()) {
            return factory.getValidator().validate(check);
        }
    }
}
