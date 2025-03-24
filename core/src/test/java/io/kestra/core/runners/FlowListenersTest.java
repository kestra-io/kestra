package io.kestra.core.runners;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.property.Property;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.core.junit.annotations.KestraTest;
import lombok.SneakyThrows;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.plugin.core.debug.Return;
import io.kestra.core.utils.IdUtils;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
abstract public class FlowListenersTest {
    @Inject
    protected FlowRepositoryInterface flowRepository;

    @Inject
    protected PluginDefaultService pluginDefaultService;

    protected static FlowWithSource create(String flowId, String taskId) {
        Flow flow = Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .revision(1)
            .tasks(Collections.singletonList(Return.builder()
                .id(taskId)
                .type(Return.class.getName())
                .format(Property.of("test"))
                .build()))
            .build();
        return flow.withSource(flow.generateSource());
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
            assertThat(count.get(), is(0));
            assertThat(flowListenersService.flows().size(), is(0));
        });

        // resend on startup done for kafka
        if (flowListenersService.getClass().getName().equals("io.kestra.ee.runner.kafka.KafkaFlowListeners")) {
            wait(ref, () -> {
                assertThat(count.get(), is(0));
                assertThat(flowListenersService.flows().size(), is(0));
            });
        }

        // create first
        FlowWithSource first = create("first_" + IdUtils.create(), "test");
        FlowWithSource firstUpdated = create(first.getId(), "test2");


        flowRepository.create(first, first.generateSource(), pluginDefaultService.injectDefaults(first.withSource(first.generateSource())));
        wait(ref, () -> {
            assertThat(count.get(), is(1));
            assertThat(flowListenersService.flows().size(), is(1));
        });

        // create the same id than first, no additional flows
        first = flowRepository.update(firstUpdated, first, firstUpdated.generateSource(), pluginDefaultService.injectDefaults(firstUpdated.withSource(firstUpdated.generateSource())));
        wait(ref, () -> {
            assertThat(count.get(), is(1));
            assertThat(flowListenersService.flows().size(), is(1));
            assertThat(flowListenersService.flows().getFirst().getTasks().getFirst().getId(), is("test2"));
        });

        Flow second = create("second_" + IdUtils.create(), "test");
        // create a new one
        flowRepository.create(second, second.generateSource(), pluginDefaultService.injectDefaults(second.withSource(second.generateSource())));
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
        flowRepository.create(first, first.generateSource(), pluginDefaultService.injectDefaults(first.withSource(first.generateSource())));
        wait(ref, () -> {
            assertThat(count.get(), is(2));
            assertThat(flowListenersService.flows().size(), is(2));
        });

        Flow withTenant = first.toBuilder().tenantId("some-tenant").build();
        flowRepository.create(withTenant, withTenant.generateSource(), pluginDefaultService.injectDefaults(withTenant.withSource(withTenant.generateSource())));
        wait(ref, () -> {
            assertThat(count.get(), is(3));
            assertThat(flowListenersService.flows().size(), is(3));
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
