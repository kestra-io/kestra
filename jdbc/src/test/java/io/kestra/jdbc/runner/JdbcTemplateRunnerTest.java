package io.kestra.jdbc.runner;

import io.kestra.core.Helpers;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.runners.StandAloneRunner;
import io.kestra.plugin.core.flow.TemplateTest;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junitpioneer.jupiter.RetryingTest;

import java.io.IOException;
import java.net.URISyntaxException;

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // must be per-class to allow calling once init() which took a lot of time
public abstract class JdbcTemplateRunnerTest {
    @Inject
    private StandAloneRunner runner;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logsQueue;

    @Inject
    private TemplateRepositoryInterface templateRepository;

    @BeforeAll
    void init() throws IOException, URISyntaxException {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();

        TestsUtils.loads(repositoryLoader, Helpers.class.getClassLoader().getResource("flows/templates"));
        runner.setSchedulerEnabled(false);
        runner.run();
    }

    @Test
    void withTemplate() throws Exception {
        TemplateTest.withTemplate(runnerUtils, templateRepository, repositoryLoader, logsQueue);
    }

    @RetryingTest(5) // flaky on MySQL
    void withFailedTemplate() throws Exception {
        TemplateTest.withFailedTemplate(runnerUtils, templateRepository, repositoryLoader, logsQueue);
    }
}
