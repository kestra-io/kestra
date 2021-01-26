package org.kestra.core.runners;

import org.junit.jupiter.api.Test;

import javax.inject.Inject;

public class MultipleConditionTriggerTest extends AbstractMemoryRunnerTest {
    @Inject
    private MultipleConditionTriggerCaseTest runnerCaseTest;

    @Test
    void trigger() throws Exception {
        runnerCaseTest.trigger();
    }
}
