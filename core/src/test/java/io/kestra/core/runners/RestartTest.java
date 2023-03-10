package io.kestra.core.runners;

import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

public class RestartTest extends AbstractMemoryRunnerTest {
    @Inject
    private RestartCaseTest restartCaseTest;

    @Test
    void restartFailedThenSuccess() throws Exception {
        restartCaseTest.restartFailedThenSuccess();
    }

    @Test
    void restartFailedThenFailureWithGlobalErrors() throws Exception {
        restartCaseTest.restartFailedThenFailureWithGlobalErrors();
    }

    @Test
    void restartFailedThenFailureWithLocalErrors() throws Exception {
        restartCaseTest.restartFailedThenFailureWithLocalErrors();
    }

    @Test
    void replay() throws Exception {
        restartCaseTest.replay();
    }
}
