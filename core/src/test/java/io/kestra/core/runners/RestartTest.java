package io.kestra.core.runners;

import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

public class RestartTest extends AbstractMemoryRunnerTest {
    @Inject
    private RestartCaseTest restartCaseTest;

    @Test
    void restartFailed() throws Exception {
        restartCaseTest.restartFailed();
    }

    @Test
    void replay() throws Exception {
        restartCaseTest.replay();
    }
}
