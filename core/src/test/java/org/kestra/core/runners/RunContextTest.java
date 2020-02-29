package org.kestra.core.runners;

import org.exparity.hamcrest.date.ZonedDateTimeMatchers;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRunAttempt;
import org.kestra.core.models.executions.metrics.Counter;
import org.kestra.core.models.executions.metrics.Timer;
import org.slf4j.event.Level;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.number.OrderingComparison.greaterThan;

class RunContextTest extends AbstractMemoryRunnerTest {
    @Test
    void logs() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "logs");

        assertThat(execution.getTaskRunList(), hasSize(3));

        assertThat(execution.getTaskRunList().get(0).getAttempts().get(0).getLogs(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getAttempts().get(0).getLogs().get(0).getLevel(), is(Level.TRACE));
        assertThat(execution.getTaskRunList().get(0).getAttempts().get(0).getLogs().get(0).getMessage(), is("first t1"));

        assertThat(execution.getTaskRunList().get(1).getAttempts().get(0).getLogs(), hasSize(1));
        assertThat(execution.getTaskRunList().get(1).getAttempts().get(0).getLogs().get(0).getLevel(), is(Level.WARN));
        assertThat(execution.getTaskRunList().get(1).getAttempts().get(0).getLogs().get(0).getMessage(), is("second org.kestra.core.tasks.debugs.Echo"));

        assertThat(execution.getTaskRunList().get(2).getAttempts().get(0).getLogs(), hasSize(1));
        assertThat(execution.getTaskRunList().get(2).getAttempts().get(0).getLogs().get(0).getLevel(), is(Level.ERROR));
        assertThat(execution.getTaskRunList().get(2).getAttempts().get(0).getLogs().get(0).getMessage(), is("third logs"));
    }

    @Test
    void variables() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "return");

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
        Execution execution = runnerUtils.runOne("org.kestra.tests", "return");

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
