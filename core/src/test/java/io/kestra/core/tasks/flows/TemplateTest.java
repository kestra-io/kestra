package io.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.ListenersTest;
import io.kestra.core.tasks.debugs.Echo;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.runners.RunnerUtils;
import org.slf4j.event.Level;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class TemplateTest extends AbstractMemoryRunnerTest {
    @Inject
    protected TemplateRepositoryInterface templateRepository;


    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    protected QueueInterface<LogEntry> logQueue;

    public static final io.kestra.core.models.templates.Template TEMPLATE_1 = io.kestra.core.models.templates.Template.builder()
        .id("template")
        .namespace("io.kestra.tests")
        .tasks(Collections.singletonList(Echo.builder().id("test").type(Echo.class.getName()).format("{{ parent.outputs.args['my-forward'] }}").build())).build();

    public static void withTemplate(RunnerUtils runnerUtils, TemplateRepositoryInterface templateRepository, LocalFlowRepositoryLoader repositoryLoader, QueueInterface<LogEntry> logQueue) throws TimeoutException, IOException, URISyntaxException {
        templateRepository.create(TEMPLATE_1);
        repositoryLoader.load(Objects.requireNonNull(ListenersTest.class.getClassLoader().getResource("flows/tests/with-template.yaml")));

        List<LogEntry> logs = new ArrayList<>();
        logQueue.receive(logs::add);


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
        assertThat(logs.stream().filter(logEntry -> logEntry.getMessage().equals("myString")).findFirst().orElseThrow().getLevel(), is(Level.ERROR));
    }

    @Test
    void withTemplate() throws TimeoutException, IOException, URISyntaxException {
        TemplateTest.withTemplate(runnerUtils, templateRepository, repositoryLoader, logQueue);
    }
}
