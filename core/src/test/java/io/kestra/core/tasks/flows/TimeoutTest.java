package io.kestra.core.tasks.flows;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.services.TaskDefaultService;
import io.kestra.core.tasks.scripts.Bash;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class TimeoutTest extends AbstractMemoryRunnerTest {
    @Inject
    FlowRepositoryInterface flowRepository;

    @Inject
    TaskDefaultService taskDefaultService;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> workerTaskLogQueue;

    @Test
    void timeout() throws TimeoutException {
        List<LogEntry> logs = new ArrayList<>();
        workerTaskLogQueue.receive(logs::add);

        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .revision(1)
            .tasks(Collections.singletonList(Bash.builder()
                .id("test")
                .type(Bash.class.getName())
                .commands(new String[]{"sleep 100"})
                .timeout(Duration.ofNanos(100000))
                .build()))
            .build();

        flowRepository.create(flow, flow.generateSource(), taskDefaultService.injectDefaults(flow));

        Execution execution = runnerUtils.runOne(flow.getNamespace(), flow.getId());

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        List<LogEntry> matchingLogs = TestsUtils.awaitLogs(logs, logEntry -> logEntry.getMessage().contains("Timeout"), 2);
        assertThat(matchingLogs.size(), is(2));
    }
}
