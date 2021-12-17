package io.kestra.core.tasks.flows;

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
}
