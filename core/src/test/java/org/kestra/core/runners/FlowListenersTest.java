package org.kestra.core.runners;

import io.micronaut.test.annotation.MicronautTest;
import lombok.SneakyThrows;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.services.FlowListenersInterface;
import org.kestra.core.tasks.debugs.Return;
import org.kestra.core.utils.IdUtils;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
abstract public class FlowListenersTest {
    @Inject
    FlowRepositoryInterface flowRepository;

    private static Flow create(String flowId, String taskId) {
        return Flow.builder()
            .id(flowId)
            .namespace("org.kestra.unittest")
            .revision(1)
            .tasks(Collections.singletonList(Return.builder()
                .id(taskId)
                .type(Return.class.getName())
                .format("test")
                .build()))
            .build();
    }

    public void suite(FlowListenersInterface flowListenersService) {
        AtomicInteger count = new AtomicInteger();
        var ref = new Ref();

        flowListenersService.listen(flows -> {
            count.set(flows.size());
            ref.countDownLatch.countDown();
        });

        // initial state
        wait(ref, () -> {
            assertThat(count.get(), is(0));
            assertThat(flowListenersService.flows().size(), is(0));
        });

        // create first
        Flow first = create(IdUtils.create(), "test");

        flowRepository.create(first);
        wait(ref, () -> {
            assertThat(count.get(), is(1));
            assertThat(flowListenersService.flows().size(), is(1));
        });

        // create the same id than first, no additional flows
        first = flowRepository.update(create(first.getId(), "test2"), first);
        wait(ref, () -> {
            assertThat(count.get(), is(1));
            assertThat(flowListenersService.flows().size(), is(1));
        });

        // create a new one
        flowRepository.create(create(IdUtils.create(), "test"));
        wait(ref, () -> {
            assertThat(count.get(), is(2));
            assertThat(flowListenersService.flows().size(), is(2));
        });

        // delete first
        Flow deleted = flowRepository.delete(first);
        wait(ref, () -> {
            assertThat(count.get(), is(1));
            assertThat(flowListenersService.flows().size(), is(1));
        });

        // restore must works
        flowRepository.create(first.withRevision(deleted.getRevision() + 1));
        wait(ref, () -> {
            assertThat(count.get(), is(2));
            assertThat(flowListenersService.flows().size(), is(2));
        });
    }

    public static class Ref {
        CountDownLatch countDownLatch = new CountDownLatch(1);
    }

    @SneakyThrows
    private void wait(Ref ref, Runnable run) {
        ref.countDownLatch.await();
        run.run();
        ref.countDownLatch = new CountDownLatch(1);
    }
}
