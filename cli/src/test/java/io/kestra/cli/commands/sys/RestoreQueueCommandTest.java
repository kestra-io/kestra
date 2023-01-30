package io.kestra.cli.commands.sys;

import io.kestra.core.services.TaskDefaultService;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.kestra.core.contexts.KestraClassLoader;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.utils.IdUtils;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class RestoreQueueCommandTest {
    @BeforeAll
    static void init() {
        if (!KestraClassLoader.isInit()) {
            KestraClassLoader.create(RestoreQueueCommandTest.class.getClassLoader());
        }
    }

    static Flow create() {
        return Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .revision(1)
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();
    }

    @SuppressWarnings("unchecked")
    @Test
    void run() throws InterruptedException {
        final int COUNT = 5;

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            FlowRepositoryInterface flowRepository = ctx.getBean(FlowRepositoryInterface.class);
            QueueInterface<Flow> flowQueue = ctx.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.FLOW_NAMED));
            TaskDefaultService taskDefaultService = ctx.getBean(TaskDefaultService.class);

            AtomicInteger atomicInteger = new AtomicInteger();

            for (int i = 0; i < COUNT; i++) {
                Flow flow = create();
                flowRepository.create(flow, flow.generateSource(), taskDefaultService.injectDefaults(flow));
            }
            CountDownLatch countDownLatch = new CountDownLatch(COUNT);

            flowQueue.receive(e -> {
                atomicInteger.incrementAndGet();
                countDownLatch.countDown();
            });

            PicocliRunner.call(RestoreQueueCommand.class, ctx);

            countDownLatch.await();
            assertThat(atomicInteger.get(), is(COUNT));
        }
    }
}
