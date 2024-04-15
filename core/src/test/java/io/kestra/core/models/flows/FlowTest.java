package io.kestra.core.models.flows;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolationException;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
class FlowTest {
    @Inject
    YamlFlowParser yamlFlowParser = new YamlFlowParser();

    @Inject
    ModelValidator modelValidator;

    @Test
    void duplicate() {
        Flow flow = this.parse("flows/invalids/duplicate.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), containsString("Duplicate task id with name [date, listen]"));
        assertThat(validate.get().getMessage(), containsString("Duplicate trigger id with name [trigger]"));
    }

    @Test
    void duplicateInputs() {
        Flow flow = this.parse("flows/invalids/duplicate-inputs.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), containsString("Duplicate input with name [first_input]"));
    }

    @Test
    void duplicateParallel() {
        Flow flow = this.parse("flows/invalids/duplicate-parallel.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), containsString("Duplicate task id with name [t3]"));
    }

    @Test
    void duplicateUpdate() {
        Flow flow = this.parse("flows/valids/logs.yaml");
        Flow updated = this.parse("flows/invalids/duplicate.yaml");
        Optional<ConstraintViolationException> validate = flow.validateUpdate(updated);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), containsString("Illegal flow id update"));
    }


    @Test
    void switchTaskInvalid() {
        Flow flow = this.parse("flows/invalids/switch-invalid.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), containsString("tasks[0]: No task defined, neither cases or default have any tasks"));
    }

    @Test
    void workingDirectoryTaskInvalid() {
        Flow flow = this.parse("flows/invalids/workingdirectory-invalid.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), containsString("tasks[0]: Only runnable tasks are allowed as children of a WorkingDirectory task"));
    }

    @Test
    void workingDirectoryNoTasks() {
        Flow flow = this.parse("flows/invalids/workingdirectory-no-tasks.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), containsString("tasks[0]: The 'tasks' property cannot be empty"));
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

    @Test
    void workerGroup() {
        Flow flow = this.parse("flows/invalids/worker-group.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), equalTo("tasks[0].workerGroup: Worker Group is an Enterprise Edition functionality"));
    }

    @Test
    void allTasksWithChildsAndTriggerIds() {
        Flow flow = this.parse("flows/valids/trigger-flow-listener-no-inputs.yaml");
        List<String> all = flow.allTasksWithChildsAndTriggerIds();

        assertThat(all.size(), is(3));
    }

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return yamlFlowParser.parse(file, Flow.class);
    }
}