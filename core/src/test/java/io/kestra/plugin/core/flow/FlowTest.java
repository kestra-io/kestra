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
    @LoadFlows(value = {"flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml",
        "flows/valids/switch.yaml"}, tenantId = "tenant1")
    void waitFailed() throws Exception {
        flowCaseTest.waitFailed("tenant1");
    }

    @Test
    @LoadFlows(value = {"flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml",
        "flows/valids/switch.yaml"}, tenantId = "tenant2")
    void invalidOutputs() throws Exception {
        flowCaseTest.invalidOutputs("tenant2");
    }

    @Test
    @LoadFlows(value = {"flows/valids/task-flow.yaml",
        "flows/valids/task-flow-inherited-labels.yaml",
        "flows/valids/switch.yaml"}, tenantId = "tenant3")
    void noLabels() throws Exception {
        flowCaseTest.noLabels("tenant3");
    }

    @Test
    @LoadFlows({"flows/valids/subflow-old-task-name.yaml",
        "flows/valids/minimal.yaml"})
    void oldTaskName() throws Exception {
        flowCaseTest.oldTaskName();
    }
}
