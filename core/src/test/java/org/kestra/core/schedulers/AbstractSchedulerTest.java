package org.kestra.core.schedulers;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.annotation.MicronautTest;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.ExecutionRepositoryInterface;
import org.kestra.core.repositories.TriggerRepositoryInterface;
import org.kestra.core.services.FlowListenersService;
import org.kestra.core.tasks.debugs.Return;
import org.kestra.core.utils.ExecutorsUtils;
import org.kestra.core.utils.IdUtils;

import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;

@MicronautTest
abstract class AbstractSchedulerTest {
    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    protected ExecutorsUtils executorsUtils;

    @Inject
    protected TriggerRepositoryInterface triggerContextRepository;

    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    @Inject
    protected FlowListenersService flowListenersService;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    protected  static Flow createFlow(List<AbstractTrigger> triggers) {
        return Flow.builder()
            .id(IdUtils.create())
            .namespace("org.kestra.unittest")
            .revision(1)
            .triggers(triggers)
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();
    }
}
