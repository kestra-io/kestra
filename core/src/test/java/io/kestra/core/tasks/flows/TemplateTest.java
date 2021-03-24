package io.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.tasks.debugs.Return;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class TemplateTest extends AbstractMemoryRunnerTest {
    @Inject
    protected TemplateRepositoryInterface templateRepository;

    public static final io.kestra.core.models.templates.Template TEMPLATE = io.kestra.core.models.templates.Template.builder()
        .id("template")
        .namespace("io.kestra.tests")
        .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("{{ parent.outputs.args.my-forward }}").build())).build();

    public static void withTemplate(RunnerUtils runnerUtils, TemplateRepositoryInterface templateRepository) throws TimeoutException {
        templateRepository.create(TEMPLATE);

        Execution execution = runnerUtils.runOne(
            "io.kestra.tests",
            "with-template",
            null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, ImmutableMap.of(
                "with-string", "myString",
                "with-optional", "myOpt"
            )),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(4));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(
            execution.findTaskRunsByTaskId("test").get(0).getOutputs().get("value"),
            is("myString")
        );
    }

    @Test
    void withTemplate() throws TimeoutException {
        TemplateTest.withTemplate(runnerUtils, templateRepository);
    }
}
