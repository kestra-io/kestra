package io.kestra.plugin.core.state;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class StateTest {
    @Inject
    RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        Get get = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, get, Map.of(
            "key", "test",
            "inc", 1
        ));

        Get.Output getOutput = get.run(runContext);
        assertThat(getOutput.getCount(), is(0));

        Set set = Set.builder()
            .id(IdUtils.create())
            .type(Set.class.toString())
            .data(new Property<>(Map.of(
                "{{ inputs.key }}", "{{ inputs.inc }}"
            )))
            .build();
        Set.Output setOutput = set.run(runContext);
        assertThat(setOutput.getCount(), is(1));

        get = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.toString())
            .build();
        getOutput = get.run(runContext);
        assertThat(getOutput.getCount(), is(1));
        assertThat(getOutput.getData().get("test"), is("1"));

        set = Set.builder()
            .id(IdUtils.create())
            .type(Set.class.toString())
            .data(new Property<>(Map.of(
                "{{ inputs.key }}", "2",
                "test2", "3"
            )))
            .build();

        setOutput = set.run(runContext);
        assertThat(setOutput.getCount(), is(2));

        get = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.toString())
            .build();

        getOutput = get.run(runContext);

        assertThat(getOutput.getCount(), is(2));
        assertThat(getOutput.getData().get("test"), is("2"));
        assertThat(getOutput.getData().get("test2"), is("3"));

        Delete delete = Delete.builder()
            .id(IdUtils.create())
            .type(Get.class.toString())
            .build();

        Delete.Output deleteRun = delete.run(runContext);
        assertThat(deleteRun.getDeleted(), is(true));


        get = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.toString())
            .build();

        getOutput = get.run(runContext);

        assertThat(getOutput.getCount(), is(0));
    }

    @Test
    void deleteThrow() {
        Delete task = Delete.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .name(new Property<>(IdUtils.create()))
            .errorOnMissing(Property.of(true))
            .build();

        assertThrows(FileNotFoundException.class, () -> {
            task.run(TestsUtils.mockRunContext(runContextFactory, task, Map.of()));
        });
    }

    @Test
    void getThrow() {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .name(new Property<>(IdUtils.create()))
            .errorOnMissing(Property.of(true))
            .build();

        assertThrows(FileNotFoundException.class, () -> {
            task.run(TestsUtils.mockRunContext(runContextFactory, task, Map.of()));
        });
    }
}