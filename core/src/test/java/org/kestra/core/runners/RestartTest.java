package org.kestra.core.runners;

import org.junit.jupiter.api.Test;

import javax.inject.Inject;

public class RestartTest extends AbstractMemoryRunnerTest {
    @Inject
    private RunnerCaseTest runnerCaseTest;

    @Test
    void restart() throws Exception {
        runnerCaseTest.restart();
    }
}
