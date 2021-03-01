package org.kestra.core.tasks.flows;

import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.tasks.scripts.Bash;
import org.kestra.core.utils.IdUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import javax.inject.Named;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TimeoutTest extends AbstractMemoryRunnerTest {
    @Inject
    FlowRepositoryInterface flowRepository;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> workerTaskLogQueue;

    @Test
    void timeout() throws TimeoutException {
        List<LogEntry> logs = new ArrayList<>();
        workerTaskLogQueue.receive(logs::add);

        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("org.kestra.unittest")
            .revision(1)
            .tasks(Collections.singletonList(Bash.builder()
                .id("test")
                .type(Bash.class.getName())
                .commands(new String[]{"sleep 100"})
                .timeout(Duration.ofNanos(100000))
                .build()))
            .build();

        flowRepository.create(flow);

        Execution execution = runnerUtils.runOne(flow.getNamespace(), flow.getId());

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(logs.stream().filter(logEntry -> logEntry.getMessage().contains("Timeout")).count(), is(2L));
    }
}
