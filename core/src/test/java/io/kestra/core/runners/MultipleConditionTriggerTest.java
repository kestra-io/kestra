package io.kestra.core.runners;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

@MicronautTest
public class MultipleConditionTriggerTest extends AbstractMemoryRunnerTest {
    @Inject
    private MultipleConditionTriggerCaseTest runnerCaseTest;

    @Test
    void trigger() throws Exception {
        runnerCaseTest.trigger();
    }

    @Test
    void triggerFailed() throws Exception {
        runnerCaseTest.failed();
    }
}
