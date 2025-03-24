package io.kestra.plugin.core.flow;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junitpioneer.jupiter.RetryingTest;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest(startRunner = true)
class TimeoutTest {
    @Inject
    FlowRepositoryInterface flowRepository;

    @Inject
    PluginDefaultService pluginDefaultService;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> workerTaskLogQueue;

    @Inject
    private RunnerUtils runnerUtils;

    @RetryingTest(5) // Flaky on CI but never locally even with 100 repetitions
    void timeout() throws TimeoutException, QueueException {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(workerTaskLogQueue, either -> logs.add(either.getLeft()));

        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .revision(1)
            .tasks(Collections.singletonList(Sleep.builder()
                .id("test")
                .type(Sleep.class.getName())
                .duration(Duration.ofSeconds(100))
                .timeout(Property.of(Duration.ofNanos(100000)))
                .build()))
            .build();

        flowRepository.create(flow, flow.generateSource(), pluginDefaultService.injectDefaults(flow.withSource(flow.generateSource())));

        Execution execution = runnerUtils.runOne(flow.getTenantId(), flow.getNamespace(), flow.getId());

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        List<LogEntry> matchingLogs = TestsUtils.awaitLogs(logs, logEntry -> logEntry.getMessage().contains("Timeout"), 2);
        receive.blockLast();
        assertThat(matchingLogs.size(), is(2));
    }
}
