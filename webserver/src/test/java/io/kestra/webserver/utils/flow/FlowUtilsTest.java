package io.kestra.webserver.utils.flow;

import com.google.common.collect.ImmutableList;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.utils.IdUtils;

import java.util.Collections;

public class FlowUtilsTest {
    public static Flow generateFlow() {
        return generateFlow("io.kestra.tests", "inputTest");
    }

    public static Flow generateFlow(String namespace, String inputName) {
        return generateFlow(IdUtils.create(), namespace, inputName);
    }

    public static Flow generateFlow(String friendlyId, String namespace, String inputName) {
        return Flow.builder()
            .id(friendlyId)
            .namespace(namespace)
            .inputs(ImmutableList.of(StringInput.builder().type(Input.Type.STRING).name(inputName).build()))
            .tasks(Collections.singletonList(generateTask("test", "test")))
            .build();
    }

    public static Task generateTask(String id, String format) {
        return Return.builder()
            .id(id)
            .type(Return.class.getName())
            .format(format)
            .build();
    }
}
