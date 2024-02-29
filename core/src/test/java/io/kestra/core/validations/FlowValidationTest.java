package io.kestra.core.validations;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolationException;
import java.io.File;
import java.net.URL;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@MicronautTest
class FlowValidationTest {
    @Inject
    private ModelValidator modelValidator;
    @Inject
    YamlFlowParser yamlFlowParser = new YamlFlowParser();

    @Test
    void invalidRecursiveFlow() {
        Flow flow = this.parse("flows/invalids/recursive-flow.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getMessage(), containsString(": Invalid Flow: Recursive call to flow [io.kestra.tests.recursive-flow]"));
    }

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return yamlFlowParser.parse(file, Flow.class);
    }
}
