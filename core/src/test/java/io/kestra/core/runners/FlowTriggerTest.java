package io.kestra.core.runners;

import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

public class FlowTriggerTest extends AbstractMemoryRunnerTest {
    @Inject
    private FlowTriggerCaseTest runnerCaseTest;

    @Test
    void trigger() throws Exception {
        runnerCaseTest.trigger();
    }
}
