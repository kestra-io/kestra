package org.floworc.core.runners;

import com.devskiller.friendly_id.FriendlyId;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.models.flows.State;
import org.floworc.core.queues.QueueFactoryInterface;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.core.utils.Await;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class RunnerUtils {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    private FlowRepositoryInterface flowRepository;

    public Execution runOne(String flowId) throws TimeoutException {
        return this.runOne(
            flowRepository
                .findById(flowId)
                .orElseThrow(() -> new IllegalArgumentException("Unable to find flow '" + flowId + "'"))
        );
    }

    private Execution runOne(Flow flow) throws TimeoutException {
        Execution execution = Execution.builder()
            .id(FriendlyId.createFriendlyId())
            .flowId(flow.getId())
            .state(new State())
            .build();

        AtomicReference<Execution> receive = new AtomicReference<>();

        Runnable cancel = this.executionQueue.receive(StandAloneRunner.class, current -> {
            if (current.getId().equals(execution.getId()) && current.getState().isTerninated()) {
                receive.set(current);
            }
        });

        this.executionQueue.emit(execution);

        Await.until(() -> receive.get() != null, 5 * 1000);

        cancel.run();

        return receive.get();
    }

}
