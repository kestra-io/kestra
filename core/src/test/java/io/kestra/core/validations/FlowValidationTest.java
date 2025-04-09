package io.kestra.core.validations;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlParser;
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

        assertThat(validate.isPresent()).isEqualTo(true);
        assertThat(validate.get().getMessage()).contains(": Invalid Flow: Recursive call to flow [io.kestra.tests.recursive-flow]");
    }

    @Test
    void systemLabelShouldFailValidation() {
        Flow flow = this.parse("flows/invalids/system-labels.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent()).isEqualTo(true);
        assertThat(validate.get().getMessage()).contains("System labels can only be set by Kestra itself, offending label: system.label=system_key");
        assertThat(validate.get().getMessage()).contains("System labels can only be set by Kestra itself, offending label: system.id=id");
    }

    @Test
    void validFlowShouldSucceed() {
        Flow flow = this.parse("flows/valids/minimal.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent()).isEqualTo(false);
    }

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return YamlParser.parse(file, Flow.class);
    }
}
