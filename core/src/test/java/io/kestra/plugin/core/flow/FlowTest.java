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
    @LoadFlows(value = {"flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml",
        "flows/valids/switch.yaml"}, tenantId = "waitsuccess")
    void waitSuccess() throws Exception {
        flowCaseTest.waitSuccess("waitsuccess");
    }

    @Test
    @LoadFlows(value = {"flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml",
        "flows/valids/switch.yaml"}, tenantId = "waitfailed")
    void waitFailed() throws Exception {
        flowCaseTest.waitFailed("waitfailed");
    }

    @Test
    @LoadFlows(value = {"flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml",
        "flows/valids/switch.yaml"}, tenantId = "invalidoutputs")
    void invalidOutputs() throws Exception {
        flowCaseTest.invalidOutputs("invalidoutputs");
    }

    @Test
    @LoadFlows(value = {"flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml",
        "flows/valids/switch.yaml"}, tenantId = "nolabels")
    void noLabels() throws Exception {
        flowCaseTest.noLabels("nolabels");
    }

    @Test
    @LoadFlows(value = {"flows/valids/subflow-old-task-name.yaml",
        "flows/valids/minimal.yaml"}, tenantId = "oldtaskname")
    void oldTaskName() throws Exception {
        flowCaseTest.oldTaskName("oldtaskname");
    }
}
