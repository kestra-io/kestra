package io.kestra.core.runners;

import org.junit.jupiter.api.Test;

import javax.inject.Inject;

public class RestartTest extends AbstractMemoryRunnerTest {
    @Inject
    private RestartCaseTest restartCaseTest;

    @Test
    void restart() throws Exception {
        restartCaseTest.restart();
    }
}
