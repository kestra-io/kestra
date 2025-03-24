package io.kestra.plugin.core.state;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class StateNamespaceTest {
    @Inject
    RunContextFactory runContextFactory;

    private RunContext runContextFlow1(Task task) {
        return TestsUtils.mockRunContext(runContextFactory, task, Map.of());
    }

    private RunContext runContextFlow2(Task task) {
        return TestsUtils.mockRunContext(runContextFactory, task, Map.of());
    }

    @Test
    void run() throws Exception {
        Set set = Set.builder()
            .id(IdUtils.create())
            .type(Set.class.getSimpleName())
            .namespace(Property.of(true))
            .data(Property.of(Map.of(
                "john", "doe"
            )))
            .build();
        Set.Output setOutput = set.run(runContextFlow1(set));
        assertThat(setOutput.getCount(), is(1));

        Get get = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getSimpleName())
            .namespace(Property.of(true))
            .build();
        Get.Output getOutput = get.run(runContextFlow2(get));
        assertThat(getOutput.getCount(), is(1));
        assertThat(getOutput.getData().get("john"), is("doe"));

        get = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getSimpleName())
            .build();
        getOutput = get.run(runContextFlow2(get));
        assertThat(getOutput.getCount(), is(0));
    }
}