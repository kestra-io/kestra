package org.kestra.core.models.flows;

import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.kestra.core.serializers.YamlFlowParser;
import org.kestra.core.utils.TestsUtils;

import java.io.File;
import java.net.URL;
import java.util.Optional;
import javax.inject.Inject;
import javax.validation.ConstraintViolationException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@MicronautTest
class FlowTest {
    @Inject
    YamlFlowParser yamlFlowParser = new YamlFlowParser();

    @Test
    void duplicate() {
        Flow flow = this.parse("flows/invalids/duplicate.yaml");
        Optional<ConstraintViolationException> validate = flow.validate();

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), containsString("Duplicate task id with name [date]"));
    }

    @Test
    void duplicateUpdate() {
        Flow flow = this.parse("flows/valids/logs.yaml");
        Flow updated = this.parse("flows/invalids/duplicate.yaml");
        Optional<ConstraintViolationException> validate = flow.validateUpdate(updated);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(2));

        assertThat(validate.get().getMessage(), containsString("Duplicate task id with name [date]"));
        assertThat(validate.get().getMessage(), containsString("Illegal flow id update"));
    }


    @Test
    void taskInvalid() {
        Flow flow = this.parse("flows/invalids/switch-invalid.yaml");
        Optional<ConstraintViolationException> validate = flow.validate();

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), containsString("switch.tasks: No task defined"));
    }

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return yamlFlowParser.parse(file);
    }
}