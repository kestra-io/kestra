package io.kestra.plugin.core.flow;

import io.kestra.core.runners.AbstractMemoryRunnerTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

public class FlowTest extends AbstractMemoryRunnerTest {
    @Inject
    FlowCaseTest flowCaseTest;

    @Test
    public void waitSuccess() throws Exception {
        flowCaseTest.waitSuccess();
    }

    @Test
    public void waitFailed() throws Exception {
        flowCaseTest.waitFailed();
    }

    @Test
    public void invalidOutputs() throws Exception {
        flowCaseTest.invalidOutputs();
    }

    @Test
    public void noLabels() throws Exception {
        flowCaseTest.noLabels();
    }
}
