package io.kestra.core.runners;

import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.junit.annotations.KestraTest;
import lombok.SneakyThrows;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.plugin.core.debug.Return;
import io.kestra.core.utils.IdUtils;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
abstract public class FlowListenersTest {
    @Inject
    protected FlowRepositoryInterface flowRepository;

    protected static FlowWithSource create(String flowId, String taskId) {
        FlowWithSource flow = FlowWithSource.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .revision(1)
            .tasks(Collections.singletonList(Return.builder()
                .id(taskId)
                .type(Return.class.getName())
                .format(Property.of("test"))
                .build()))
            .build();
        return flow.toBuilder().source(flow.sourceOrGenerateIfNull()).build();
    }

    public void suite(FlowListenersInterface flowListenersService) {
        flowListenersService.run();

        AtomicInteger count = new AtomicInteger();
        var ref = new Ref();

        flowListenersService.listen(flows -> {
            count.set(flows.size());
            ref.countDownLatch.countDown();
        });

        // initial state
        wait(ref, () -> {
            assertThat(count.get()).isEqualTo(0);
            assertThat(flowListenersService.flows().size()).isEqualTo(0);
        });

        // resend on startup done for kafka
        if (flowListenersService.getClass().getName().equals("io.kestra.ee.runner.kafka.KafkaFlowListeners")) {
            wait(ref, () -> {
                assertThat(count.get()).isEqualTo(0);
                assertThat(flowListenersService.flows().size()).isEqualTo(0);
            });
        }

        // create first
        FlowWithSource first = create("first_" + IdUtils.create(), "test");
        FlowWithSource firstUpdated = create(first.getId(), "test2");


        flowRepository.create(GenericFlow.of(first));
        wait(ref, () -> {
            assertThat(count.get()).isEqualTo(1);
            assertThat(flowListenersService.flows().size()).isEqualTo(1);
        });

        // create the same id than first, no additional flows
        first = flowRepository.update(GenericFlow.of(firstUpdated), first);
        wait(ref, () -> {
            assertThat(count.get()).isEqualTo(1);
            assertThat(flowListenersService.flows().size()).isEqualTo(1);
            //assertThat(flowListenersService.flows().getFirst().getFirst().getId(), is("test2"));
        });

        FlowWithSource second = create("second_" + IdUtils.create(), "test");
        // create a new one
        flowRepository.create(GenericFlow.of(second));
        wait(ref, () -> {
            assertThat(count.get()).isEqualTo(2);
            assertThat(flowListenersService.flows().size()).isEqualTo(2);
        });

        // delete first
        FlowWithSource deleted = flowRepository.delete(first);
        wait(ref, () -> {
            assertThat(count.get()).isEqualTo(1);
            assertThat(flowListenersService.flows().size()).isEqualTo(1);
        });

        // restore must works
        flowRepository.create(GenericFlow.of(first));
        wait(ref, () -> {
            assertThat(count.get()).isEqualTo(2);
            assertThat(flowListenersService.flows().size()).isEqualTo(2);
        });

        FlowWithSource withTenant = first.toBuilder().tenantId("some-tenant").build();
        flowRepository.create(GenericFlow.of(withTenant));
        wait(ref, () -> {
            assertThat(count.get()).isEqualTo(3);
            assertThat(flowListenersService.flows().size()).isEqualTo(3);
        });
    }

    public static class Ref {
        CountDownLatch countDownLatch = new CountDownLatch(1);
    }

    @SneakyThrows
    private void wait(Ref ref, Runnable run) {
        ref.countDownLatch.await(60, TimeUnit.SECONDS);
        run.run();
        ref.countDownLatch = new CountDownLatch(1);
    }
}
