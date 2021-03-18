package io.kestra.core.runners;

import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.utils.TestsUtils;
import org.slf4j.event.Level;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.number.OrderingComparison.greaterThan;

class RunContextTest extends AbstractMemoryRunnerTest {
    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> workerTaskLogQueue;

    @Test
    void logs() throws TimeoutException {
        List<LogEntry> logs = new ArrayList<>();
        List<LogEntry> filters;
        workerTaskLogQueue.receive(logs::add);

        Execution execution = runnerUtils.runOne("io.kestra.tests", "logs");

        assertThat(execution.getTaskRunList(), hasSize(3));

        filters = TestsUtils.filterLogs(logs, execution.getTaskRunList().get(0));
        assertThat(filters, hasSize(1));
        assertThat(filters.get(0).getLevel(), is(Level.TRACE));
        assertThat(filters.get(0).getMessage(), is("first t1"));

        filters = TestsUtils.filterLogs(logs, execution.getTaskRunList().get(1));
        assertThat(filters, hasSize(1));
        assertThat(filters.get(0).getLevel(), is(Level.WARN));
        assertThat(filters.get(0).getMessage(), is("second io.kestra.core.tasks.debugs.Echo"));


        filters = TestsUtils.filterLogs(logs, execution.getTaskRunList().get(2));
        assertThat(filters, hasSize(1));
        assertThat(filters.get(0).getLevel(), is(Level.ERROR));
        assertThat(filters.get(0).getMessage(), is("third logs"));
    }

    @Test
    void variables() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "return");

        assertThat(execution.getTaskRunList(), hasSize(3));

        assertThat(
            ZonedDateTime.from(ZonedDateTime.parse((String) execution.getTaskRunList().get(0).getOutputs().get("value"))),
            ZonedDateTimeMatchers.within(10, ChronoUnit.SECONDS, ZonedDateTime.now())
        );
        assertThat(execution.getTaskRunList().get(1).getOutputs().get("value"), is("task-id"));
        assertThat(execution.getTaskRunList().get(2).getOutputs().get("value"), is("return"));
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void metrics() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "return");

        TaskRunAttempt taskRunAttempt = execution.getTaskRunList()
            .get(1)
            .getAttempts()
            .get(0);
        Counter length = (Counter) taskRunAttempt.findMetrics("length").get();
        Timer duration = (Timer) taskRunAttempt.findMetrics("duration").get();

        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(length.getValue(), is(7.0D));
        assertThat(duration.getValue().getNano(), is(greaterThan(0)));
        assertThat(duration.getTags().get("format"), is("{{task.id}}"));
    }
}
