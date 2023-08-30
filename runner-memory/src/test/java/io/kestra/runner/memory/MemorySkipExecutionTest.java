package io.kestra.runner.memory;

import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.runners.SkipExecutionCaseTest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeoutException;

@MicronautTest
class MemorySkipExecutionTest  extends AbstractMemoryRunnerTest {
    @Inject
    private SkipExecutionCaseTest skipExecutionCaseTest;

    @Test
    void skipExecution() throws TimeoutException, InterruptedException {
        skipExecutionCaseTest.skipExecution();
    }
}
