package io.kestra.core.runners;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;

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

    @RetryingTest(5)
    void restartFailedThenFailureWithLocalErrors() throws Exception {
        restartCaseTest.restartFailedThenFailureWithLocalErrors();
    }

    @Test
    void replay() throws Exception {
        restartCaseTest.replay();
    }
}
