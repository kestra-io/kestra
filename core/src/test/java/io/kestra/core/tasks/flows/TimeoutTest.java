package io.kestra.core.tasks.flows;

import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.tasks.scripts.Bash;
import io.kestra.core.utils.IdUtils;

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
            .namespace("io.kestra.unittest")
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
