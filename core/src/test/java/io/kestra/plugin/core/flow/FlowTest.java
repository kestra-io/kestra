package io.kestra.plugin.core.flow;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

@KestraTest(startRunner = true)
public class FlowTest {
    @Inject
    FlowCaseTest flowCaseTest;

    @Test
    @LoadFlows({"flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml",
        "flows/valids/switch.yaml"})
    public void waitSuccess() throws Exception {
        flowCaseTest.waitSuccess();
    }

    @Test
    @LoadFlows({"flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml",
        "flows/valids/switch.yaml"})
    public void waitFailed() throws Exception {
        flowCaseTest.waitFailed();
    }

    @Test
    @LoadFlows({"flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml",
        "flows/valids/switch.yaml"})
    public void invalidOutputs() throws Exception {
        flowCaseTest.invalidOutputs();
    }

    @Test
    @LoadFlows({"flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml",
        "flows/valids/switch.yaml"})
    public void noLabels() throws Exception {
        flowCaseTest.noLabels();
    }
}
