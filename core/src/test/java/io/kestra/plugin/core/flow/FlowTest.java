package io.kestra.plugin.core.flow;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

@KestraTest(startRunner = true)
class FlowTest {
    @Inject
    FlowCaseTest flowCaseTest;

    @Test
    @LoadFlows({"flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml",
        "flows/valids/switch.yaml"})
    void waitSuccess() throws Exception {
        flowCaseTest.waitSuccess();
    }

    @Test
    @LoadFlows({"flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml",
        "flows/valids/switch.yaml"})
    void waitFailed() throws Exception {
        flowCaseTest.waitFailed();
    }

    @Test
    @LoadFlows({"flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml",
        "flows/valids/switch.yaml"})
    void invalidOutputs() throws Exception {
        flowCaseTest.invalidOutputs();
    }

    @Test
    @LoadFlows({"flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml",
        "flows/valids/switch.yaml"})
    void noLabels() throws Exception {
        flowCaseTest.noLabels();
    }

    @Test
    @LoadFlows({"flows/valids/subflow-old-task-name.yaml",
        "flows/valids/minimal.yaml"})
    void oldTaskName() throws Exception {
        flowCaseTest.oldTaskName();
    }
}
