package io.kestra.plugin.core.flow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import java.io.File;
import java.net.URL;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
public class DagTest {
    @Inject
    YamlParser yamlParser = new YamlParser();

    @Inject
    ModelValidator modelValidator;

    @Test
    @ExecuteFlow("flows/valids/dag.yaml")
    void dag(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList().size(), is(7));
    }

    @Test
    void dagCyclicDependencies() {
        Flow flow = this.parse("flows/invalids/dag-cyclicdependency.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), containsString("dag: Cyclic dependency detected: task1, task2"));
    }

    @Test
    void dagNotExistTask() {
        Flow flow = this.parse("flows/invalids/dag-notexist-task.yaml");
        Optional<ConstraintViolationException> validate = modelValidator.isValid(flow);

        assertThat(validate.isPresent(), is(true));
        assertThat(validate.get().getConstraintViolations().size(), is(1));

        assertThat(validate.get().getMessage(), containsString("dag: Not existing task id in dependency: taskX"));
    }

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return yamlParser.parse(file, Flow.class);
    }
}
