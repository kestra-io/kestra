package org.kestra.cli.commands.sys;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kestra.core.contexts.KestraClassLoader;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.tasks.debugs.Return;
import org.kestra.core.utils.IdUtils;

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
            .namespace("org.kestra.unittest")
            .revision(1)
            .tasks(Collections.singletonList(Return.builder().id("test").type(Return.class.getName()).format("test").build()))
            .build();
    }

    @Test
    void run() throws InterruptedException {
        final int COUNT = 5;

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            FlowRepositoryInterface flowRepository = ctx.getBean(FlowRepositoryInterface.class);
            QueueInterface<Flow> flowQueue = ctx.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.FLOW_NAMED));

            AtomicInteger atomicInteger = new AtomicInteger();

            for (int i = 0; i < COUNT; i++) {
                flowRepository.create(create());
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
