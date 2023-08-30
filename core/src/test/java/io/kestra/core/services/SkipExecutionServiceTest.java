package io.kestra.core.services;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
class SkipExecutionServiceTest {
    @Inject
    private SkipExecutionService skipExecutionService;

    @Test
    void test() {
        var executionToSkip = "aaabbbccc";
        var executionNotToSkip = "bbbcccddd";

        skipExecutionService.setSkipExecutions(List.of(executionToSkip));

        assertThat(skipExecutionService.skipExecution(executionToSkip), is(true));
        assertThat(skipExecutionService.skipExecution(executionNotToSkip), is(false));
    }
}