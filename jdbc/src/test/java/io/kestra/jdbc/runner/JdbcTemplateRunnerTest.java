package io.kestra.jdbc.runner;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.plugin.core.flow.TemplateTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junitpioneer.jupiter.RetryingTest;

@KestraTest(startRunner = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // must be per-class to allow calling once init() which took a lot of time
public abstract class JdbcTemplateRunnerTest {

    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logsQueue;

    @Inject
    private TemplateRepositoryInterface templateRepository;

    @Inject
    private FlowInputOutput flowIO;

    @Test
    @LoadFlows({"flows/templates/with-template.yaml"})
    void withTemplate() throws Exception {
        TemplateTest.withTemplate(runnerUtils, templateRepository, logsQueue, flowIO);
    }

    @RetryingTest(5) // flaky on MySQL
    @LoadFlows({"flows/templates/with-failed-template.yaml"})
    void withFailedTemplate() throws Exception {
        TemplateTest.withFailedTemplate(runnerUtils, logsQueue);
    }
}
