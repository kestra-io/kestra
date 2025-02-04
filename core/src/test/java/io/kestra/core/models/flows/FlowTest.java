package io.kestra.core.models.flows;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.log.Log;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class FlowTest {
    @Inject
    YamlParser yamlParser = new YamlParser();

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

        assertThat(validate.get().getMessage(), containsString("impossible: No task defined, neither cases or default have any tasks"));
    }

    @Test
    void workingDirectoryTaskInvalid() {
        Flow flow = this.parse("flows/invalids/workingdirectory-invalid.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), containsString("impossible: Only runnable tasks are allowed as children of a WorkingDirectory task"));
    }

    @Test
    void workingDirectoryNoTasks() {
        Flow flow = this.parse("flows/invalids/workingdirectory-no-tasks.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), containsString("impossible: The 'tasks' property cannot be empty"));
    }

    @Test
    void updateTask() throws InternalException {
        Flow flow = this.parse("flows/valids/each-sequential-nested.yaml");

        Flow updated = flow.updateTask("1-2-2_return", Return.builder()
            .id("1-2-2_return")
            .type(Return.class.getName())
            .format(new Property<>("{{task.id}}"))
            .build()
        );

        Task findUpdated = updated.findTaskByTaskId("1-2-2_return");

        assertThat(((Return) findUpdated).getFormat().toString(), is("{{task.id}}"));
    }

    @Test
    void workerGroup() {
        Flow flow = this.parse("flows/invalids/worker-group.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), equalTo("tasks[0].workerGroup: Worker Group is an Enterprise Edition functionality\n"));
    }

    @Test
    void allTasksWithChildsAndTriggerIds() {
        Flow flow = this.parse("flows/valids/trigger-flow-listener-no-inputs.yaml");
        List<String> all = flow.allTasksWithChildsAndTriggerIds();

        assertThat(all.size(), is(3));
    }

    @Test
    void inputValidation() {
        Flow flow = this.parse("flows/invalids/inputs-validation.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(2));

        assertThat(validate.get().getMessage(), containsString("file: no `defaults` can be set for inputs of type 'FILE'"));
        assertThat(validate.get().getMessage(), containsString("array: `itemType` cannot be `ARRAY"));
    }

    // This test is done to ensure the equals is checking the right fields and also make sure the Maps orders don't negate the equality even if they are not the same.
    // This can happen for eg. in the persistence layer that don't necessarily track LinkedHashMaps original property orders.
    @Test
    void equals() {
        Flow flowA = baseFlow();
        LinkedHashMap<String, Object> triggerInputsReverseOrder = new LinkedHashMap<>();
        triggerInputsReverseOrder.put("c", "d");
        triggerInputsReverseOrder.put("a", "b");
        Flow flowABis = baseFlow().toBuilder().revision(2).triggers(List.of(io.kestra.plugin.core.trigger.Flow.builder().inputs(triggerInputsReverseOrder).build())).build();
        assertThat(flowA.equalsWithoutRevision(flowABis), is(true));

        Flow flowB = baseFlow().toBuilder().id("b").build();
        assertThat(flowA.equalsWithoutRevision(flowB), is(false));

        Flow flowAnotherTenant = baseFlow().toBuilder().tenantId("b").build();
        assertThat(flowA.equalsWithoutRevision(flowAnotherTenant), is(false));
    }

    private static Flow baseFlow() {
        LinkedHashMap<String, Object> triggerInputs = new LinkedHashMap<>();
        triggerInputs.put("a", "b");
        triggerInputs.put("c", "d");
        return Flow.builder()
            .id("a")
            .namespace("a")
            .revision(1)
            .tenantId("a")
            .inputs(List.of(StringInput.builder().id("a").build(), StringInput.builder().id("b").build()))
            .tasks(List.of(Log.builder().message("a").build(), Log.builder().message("b").build()))
            .triggers(List.of(io.kestra.plugin.core.trigger.Flow.builder().inputs(triggerInputs).build()))
            .build();
    }

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return yamlParser.parse(file, Flow.class);
    }
}