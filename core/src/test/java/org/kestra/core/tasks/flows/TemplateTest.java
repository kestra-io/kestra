package org.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.runners.InputsTest;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesRegex;

class TemplateTest extends AbstractMemoryRunnerTest {
    private static Map<String, String> inputs = ImmutableMap.of(
        "with-string", "myString",
        "with-optional", "myOpt",
        "with-file", Objects.requireNonNull(InputsTest.class.getClassLoader().getResource("application.yml")).getPath()
    );

    @Test
    void withTemplate() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "with-template",
            null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs)
        );

        assertThat(execution.getTaskRunList(), hasSize(7));

        assertThat(
            (String) execution.findTaskRunByTaskId("file").getOutputs().get("return"),
            matchesRegex("kestra:///org/kestra/tests/with-template/executions/.*/inputs/with-file/application.yml")
        );
    }
}