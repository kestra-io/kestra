package io.kestra.core.models.flows;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.tasks.debugs.Return;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.utils.TestsUtils;

import java.io.File;
import java.net.URL;
import java.util.Optional;
import jakarta.inject.Inject;
import javax.validation.ConstraintViolationException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class FlowTest {
    @Inject
    YamlFlowParser yamlFlowParser = new YamlFlowParser();

    @Inject
    ModelValidator modelValidator;

    @Test
    void duplicate() {
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            Flow flow = this.parse("flows/invalids/duplicate.yaml");
            modelValidator.isValid(flow);
        });
        assertThat(e.getConstraintViolations().size(), is(1));

        assertThat(e.getMessage(), containsString("Duplicate task id with name [date]"));
    }

    @Test
    void duplicateParallel() {
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            Flow flow = this.parse("flows/invalids/duplicate-parallel.yaml");
            modelValidator.isValid(flow);
        });
        assertThat(e.getConstraintViolations().size(), is(1));

        assertThat(e.getMessage(), containsString("Duplicate task id with name [t3]"));
    }

    @Test
    void duplicateUpdate() {
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            Flow flow = this.parse("flows/valids/logs.yaml");
            Flow updated = this.parse("flows/invalids/duplicate.yaml");
            flow.validateUpdate(updated);
        });
        assertThat(e.getConstraintViolations().size(), is(1));

        assertThat(e.getMessage(), containsString("Duplicate task id with name [date]"));
    }

    @Test
    void invalidIdUpdate() {
        Flow flow = this.parse("flows/valids/logs.yaml");
        Flow updated = this.parse("flows/valids/each-sequential-nested.yaml");
        Optional<ConstraintViolationException> validate = flow.validateUpdate(updated);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), containsString("Illegal flow id update"));
    }


    @Test
    void taskInvalid() {
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            Flow flow = this.parse("flows/invalids/switch-invalid.yaml");
            modelValidator.isValid(flow);
        });
        assertThat(e.getConstraintViolations().size(), is(1));

        assertThat(e.getMessage(), containsString("switch.tasks: No task defined"));
    }

    @Test
    void updateTask() throws InternalException {
        Flow flow = this.parse("flows/valids/each-sequential-nested.yaml");

        Flow updated = flow.updateTask("1-2-2_return", Return.builder()
            .id("1-2-2_return")
            .type(Return.class.getName())
            .format("{{task.id}}")
            .build()
        );

        Task findUpdated = updated.findTaskByTaskId("1-2-2_return");

        assertThat(((Return) findUpdated).getFormat(), is("{{task.id}}"));
    }

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return yamlFlowParser.parse(file);
    }
}