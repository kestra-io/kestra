package io.kestra.core.validations;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolationException;

import java.io.File;
import java.net.URL;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class FlowValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void invalidRecursiveFlow() {
        Flow flow = this.parse("flows/invalids/recursive-flow.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent()).isTrue();
        assertThat(validate.get().getMessage()).contains(": Invalid Flow: Recursive call to flow [io.kestra.tests.recursive-flow]");
    }

    @Test
    void systemLabelShouldFailValidation() {
        Flow flow = this.parse("flows/invalids/system-labels.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent()).isTrue();
        assertThat(validate.get().getMessage()).contains("System labels can only be set by Kestra itself, offending label: system.label=system_key");
        assertThat(validate.get().getMessage()).contains("System labels can only be set by Kestra itself, offending label: system.id=id");
    }

    @Test
    void inputUsageWithSubtractionSymbolFailValidation() {
        Flow flow = this.parse("flows/invalids/inputs-key-with-subtraction-symbol-validation.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent()).isEqualTo(true);
        assertThat(validate.get().getMessage()).contains("Invalid input reference: use inputs[key-name] instead of inputs.key-name — keys with dashes require bracket notation, offending tasks: [hello]");
    }

    @Test
    void outputUsageWithSubtractionSymbolFailValidation() {
        Flow flow = this.parse("flows/invalids/outputs-key-with-subtraction-symbol-validation.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent()).isEqualTo(true);
        assertThat(validate.get().getMessage()).contains("Invalid output reference: use outputs[key-name] instead of outputs.key-name — keys with dashes require bracket notation, offending tasks: [use_output]");
        assertThat(validate.get().getMessage()).contains("Invalid output reference: use outputs[key-name] instead of outputs.key-name — keys with dashes require bracket notation, offending outputs: [final]");
    }

    @Test
    void validFlowShouldSucceed() {
        Flow flow = this.parse("flows/valids/minimal.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent()).isFalse();
    }

    @Test
    void shouldGetConstraintErrorGivenInputWithBothDefaultsAndPrefill() {
        // Given
        GenericFlow flow = GenericFlow.fromYaml(TenantService.MAIN_TENANT, """
            id: test
            namespace: unittest
            inputs:
              - id: input
                type: STRING
                prefill: "suggestion"
                defaults: "defaults"
            tasks: []
            """);

        // When
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        // Then
        assertThat(validate.isPresent()).isEqualTo(true);
        assertThat(validate.get().getMessage()).contains("Inputs with a default value cannot also have a prefill.");
    }

    @Test
    void shouldGetConstraintErrorGivenOptionalInputWithDefault() {
        // Given
        GenericFlow flow = GenericFlow.fromYaml(TenantService.MAIN_TENANT, """
            id: test
            namespace: unittest
            inputs:
              - id: input
                type: STRING
                defaults: "defaults"
                required: false
            tasks: []
            """);

        // When
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        // Then
        assertThat(validate.isPresent()).isEqualTo(true);
        assertThat(validate.get().getMessage()).contains("Inputs with a default value must be required, since the default is always applied.");
    }

    @Test
    void duplicatePreconditionsIdShouldFailValidation() {
        Flow flow = this.parse("flows/invalids/duplicate-preconditions.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent()).isEqualTo(true);
        assertThat(validate.get().getMessage()).contains("Duplicate preconditions with id [flows]");
    }

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return YamlParser.parse(file, Flow.class);
    }
}
