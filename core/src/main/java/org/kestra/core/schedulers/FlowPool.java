package org.kestra.core.schedulers;

import lombok.extern.slf4j.Slf4j;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.models.triggers.types.Schedule;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.runners.RunnerUtils;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Singleton
@Slf4j
public class FlowPool {
    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    private FlowRepositoryInterface flowRepository;
    protected QueueInterface<Flow> flowQueue;
    private List<Flow> flows;

    @Inject
    public FlowPool(
        FlowRepositoryInterface flowRepository,
        @Named(QueueFactoryInterface.FLOW_NAMED) QueueInterface<Flow> flowQueue
    ) {
        this.flowRepository = flowRepository;
        this.flowQueue = flowQueue;
        flows = new ArrayList<Flow>();
        for (Flow flow : flowRepository.findAll()) {
            this.upsert(flow);
        }
        this.flowQueue.receive(this::upsert);
    }

    public void remove(Flow flow) {
        for (Flow f : flows) {
            if (f.getId().equals(flow.getId())) {
                this.flows.remove(f);
                return;
            }
        }
    }

    public void upsert(Flow flow) {
        this.remove(this.findById(flow.getId()));
        if (flow.hasNextSchedule() && !flow.isDeleted()) {
            this.flows.add(flow);
        }
    }

    public Flow findById(String id) {
        for (Flow flow : flows) {
            if (flow.getId().equals(id)) {
                return flow;
            }
        }
        return null;
    }

    public int getActiveFlowCount() {
        return this.flows.size();
    }

    public void triggerReadyFlows(Instant now) {
        for (Flow flow : flows) {
            if (flow.getTriggers() != null) {
                for (Trigger trigger : flow.getTriggers()) {
                    if (((Schedule) trigger).isReady(now)) {
                        log.info(" + Triggering flow : " + flow.getId());
                        Execution current = runnerUtils.newExecution(
                            flow,
                            (triggerFlow, execution) -> runnerUtils.typedInputs(
                                triggerFlow, execution, null, null
                            )
                        );
                        executionQueue.emit(current);
                    }
                }
            }
        }

    }
}