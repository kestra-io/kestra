package io.kestra.core.runners;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
public class MultipleConditionTriggerFailedTest extends AbstractMemoryRunnerTest {
    @Inject
    private MultipleConditionTriggerCaseTest runnerCaseTest;

}
