package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Singleton
public class ScheduleDateCaseTest {
    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    public void shouldScheduleOnDate() throws QueueException, InterruptedException {
        ZonedDateTime scheduleOn = ZonedDateTime.now().plusSeconds(1);
        Flow flow = flowRepository.findById(null, "io.kestra.tests", "minimal").orElseThrow();
        Execution execution = Execution.newExecution(flow, null, null, Optional.of(scheduleOn));
        this.executionQueue.emit(execution);

        assertThat(execution.getState().getCurrent(), is(State.Type.CREATED));
        assertThat(execution.getScheduleDate(), is(scheduleOn.toInstant()));

        CountDownLatch latch1 = new CountDownLatch(1);

        Flux<Execution> receive = TestsUtils.receive(executionQueue, e -> {
            if (e.getLeft().getId().equals(execution.getId())) {
                if (e.getLeft().getState().getCurrent() == State.Type.SUCCESS) {
                    latch1.countDown();
                }
            }
        });

        latch1.await(1, TimeUnit.MINUTES);

        assertThat(receive.blockLast().getState().getCurrent(), is(State.Type.SUCCESS));
    }
}
