package io.kestra.core.validations;

import io.kestra.core.models.assets.AssetIdentifier;
import io.kestra.core.models.assets.AssetsDeclaration;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.plugin.core.log.Log;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.services.FlowService;
import jakarta.validation.ConstraintViolationException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.io.File;
import java.net.URL;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class FlowValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Inject
    private FlowService flowService;

    private static final ObjectMapper mapper = new ObjectMapper();

    // Helper class to create JsonProcessingException with location
    private static class TestJsonProcessingException extends JsonProcessingException {
        public TestJsonProcessingException(String msg, JsonLocation location) {
            super(msg, location);
        }
        public TestJsonProcessingException(String msg) {
            super(msg);
        }
    }


    @Test
    void testFormatYamlErrorMessage_WithExpectedFieldName() throws JsonProcessingException {
        JsonProcessingException e = new TestJsonProcessingException("Expected a field name", new JsonLocation(null, 100, 5, 10));
        Object dummyTarget = new Object();  // Dummy target for toConstraintViolationException

        ConstraintViolationException result = YamlParser.toConstraintViolationException(dummyTarget, "test resource", e);

        assertThat(result.getMessage()).contains("YAML syntax error: Invalid structure").contains("(at line 5)");
    }

    @Test
    void testFormatYamlErrorMessage_WithMappingStartEvent() throws JsonProcessingException {
        JsonProcessingException e = new TestJsonProcessingException("MappingStartEvent", new JsonLocation(null, 200, 3, 5));
        Object dummyTarget = new Object();

        ConstraintViolationException result = YamlParser.toConstraintViolationException(dummyTarget, "test resource", e);

        assertThat(result.getMessage()).contains("YAML syntax error: Unexpected mapping start").contains("(at line 3)");
    }

    @Test
    void testFormatYamlErrorMessage_WithScalarValue() throws JsonProcessingException {
        JsonProcessingException e = new TestJsonProcessingException("Scalar value", new JsonLocation(null, 150, 7, 12));
        Object dummyTarget = new Object();

        ConstraintViolationException result = YamlParser.toConstraintViolationException(dummyTarget, "test resource", e);

        assertThat(result.getMessage()).contains("YAML syntax error: Expected a simple value").contains("(at line 7)");
    }

    @Test
    void testFormatYamlErrorMessage_GenericError() throws JsonProcessingException {
        JsonProcessingException e = new TestJsonProcessingException("Some other error", new JsonLocation(null, 50, 2, 8));
        Object dummyTarget = new Object();

        ConstraintViolationException result = YamlParser.toConstraintViolationException(dummyTarget, "test resource", e);

        assertThat(result.getMessage()).contains("YAML parsing error: Some other error").contains("(at line 2)");
    }

    @Test
    void testFormatYamlErrorMessage_NoLocation() throws JsonProcessingException {
        JsonProcessingException e = new TestJsonProcessingException("Expected a field name");
        Object dummyTarget = new Object();

        ConstraintViolationException result = YamlParser.toConstraintViolationException(dummyTarget, "test resource", e);

        assertThat(result.getMessage()).contains("YAML syntax error: Invalid structure").doesNotContain("at line");
    }


    @Test
    void testValidateFlowWithYamlSyntaxError() {
        String invalidYaml = """
            id: test-flow
            namespace: io.kestra.unittest
            tasks:
              - id:hello
                type: io.kestra.plugin.core.log.Log
                message: {{ abc }}

            """;
            List<ValidateConstraintViolation> results = flowService.validate("my-tenant", invalidYaml);

            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getConstraints()).contains("YAML parsing error").contains("at line");
    }

    @Test
    void testValidateFlowWithUndefinedVariable() {
        String yamlWithUndefinedVar = """
            id: test-flow
            namespace: io.kestra.unittest
            tasks:
              - id: hello
                type: io.kestra.plugin.core.log.Log
                message: {{ undefinedVar }}
            """;

        List<ValidateConstraintViolation> results = flowService.validate("my-tenant", yamlWithUndefinedVar);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getConstraints()).contains("Validation error");
    }

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

    @Test
    void eeAllowsDefiningAssets() {
        Flow flow = Flow.builder()
            .id(TestsUtils.randomString())
            .namespace(TestsUtils.randomNamespace())
            .tasks(List.of(
                Log.builder()
                    .id("log")
                    .type(Log.class.getName())
                    .message("any")
                    .assets(io.kestra.core.models.property.Property.ofValue(
                        new AssetsDeclaration(true, List.of(new AssetIdentifier(null, null, "anyId")), null))
                    )
                    .build()
            ))
            .build();

        Optional<ConstraintViolationException> violations = modelValidator.isValid(flow);

        assertThat(violations.isPresent()).isEqualTo(true);
        assertThat(violations.get().getConstraintViolations().stream().map(ConstraintViolation::getMessage)).satisfiesExactly(
            message -> assertThat(message).contains("Task 'log' can't have any `assets` because assets are only available in Enterprise Edition.")
        );
    };

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return YamlParser.parse(file, Flow.class);
    }
}
