package io.kestra.core.validations;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.TaskDefault;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@MicronautTest
class TaskDefaultValidationTest {
    @Inject
    private ModelValidator modelValidator;

    @Test
    void nullValue() {
        TaskDefault taskDefault = TaskDefault.builder()
            .type("io.kestra.tests")
            .build();

        Optional<ConstraintViolationException> validate = modelValidator.isValid(taskDefault);

        assertThat(validate.isPresent(), is(true));
    }

}
