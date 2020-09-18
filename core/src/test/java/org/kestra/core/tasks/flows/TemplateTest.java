package org.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.kestra.core.exceptions.InternalException;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.repositories.TemplateRepositoryInterface;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.runners.InputsTest;
import org.kestra.core.tasks.debugs.Return;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class TemplateTest extends AbstractMemoryRunnerTest {

    @Inject
    protected TemplateRepositoryInterface templateRepository;

    private static Map<String, String> inputs = ImmutableMap.of(
        "with-string", "myString",
        "with-optional", "myOpt"
    );

    @Test
    void withTemplate() throws TimeoutException, InternalException {

        org.kestra.core.models.templates.Template template =  org.kestra.core.models.templates.Template.builder()
            .id("template")
            .namespace("org.kestra.tests")
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("{{ inputs.with-string }}").build())).build();


        org.kestra.core.models.templates.Template save = templateRepository.create(template);

        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "with-template",
            null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs)
        );

        assertThat(
            execution.findTaskRunsByTaskId("test").get(0).getOutputs().get("value"),
            is("myString")
        );
    }
}