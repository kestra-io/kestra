package io.kestra.core.runners;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.TestsUtils;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.plugin.core.debug.Return;
import io.kestra.core.utils.IdUtils;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
abstract public class FlowListenersTest {
    @Inject
    protected FlowRepositoryInterface flowRepository;

    protected static FlowWithSource create(String tenantId, String flowId, String taskId) {
        FlowWithSource flow = FlowWithSource.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .tenantId(tenantId)
            .revision(1)
            .tasks(Collections.singletonList(Return.builder()
                .id(taskId)
                .type(Return.class.getName())
                .format(Property.ofValue("test"))
                .build()))
            .build();
        return flow.toBuilder().source(flow.sourceOrGenerateIfNull()).build();
    }

    private static final Logger LOG = LoggerFactory.getLogger(FlowListenersTest.class);

    public void suite(FlowListenersInterface flowListenersService) throws TimeoutException {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        flowListenersService.run();

        AtomicInteger count = new AtomicInteger();

        flowListenersService.listen(flows -> count.set(getFlowsForTenant(flowListenersService, tenant).size()));

        // initial state
        LOG.info("-----------> wait for zero");
        Await.until(() -> count.get() == 0, Duration.ofMillis(10), Duration.ofSeconds(5));
        assertThat(getFlowsForTenant(flowListenersService, tenant).size()).isZero();

        // resend on startup done for kafka
        LOG.info("-----------> wait for zero kafka");
        if (flowListenersService.getClass().getName().equals("io.kestra.ee.runner.kafka.KafkaFlowListeners")) {
            Await.until(() -> count.get() == 0, Duration.ofMillis(10), Duration.ofSeconds(5));
            assertThat(getFlowsForTenant(flowListenersService, tenant).size()).isZero();
        }

        // create first
        LOG.info("-----------> create fist flow");
        FlowWithSource first = create(tenant, "first_" + IdUtils.create(), "test");
        FlowWithSource firstUpdated = create(tenant, first.getId(), "test2");


        flowRepository.create(GenericFlow.of(first));
        Await.until(() -> count.get() == 1, Duration.ofMillis(10), Duration.ofSeconds(5));
        assertThat(getFlowsForTenant(flowListenersService, tenant).size()).isEqualTo(1);

        // create the same id than first, no additional flows
        first = flowRepository.update(GenericFlow.of(firstUpdated), first);
        Await.until(() -> count.get() == 1, Duration.ofMillis(10), Duration.ofSeconds(5));
        assertThat(getFlowsForTenant(flowListenersService, tenant).size()).isEqualTo(1);

        FlowWithSource second = create(tenant, "second_" + IdUtils.create(), "test");
        // create a new one
        flowRepository.create(GenericFlow.of(second));
        Await.until(() -> count.get() == 2, Duration.ofMillis(10), Duration.ofSeconds(5));
        assertThat(getFlowsForTenant(flowListenersService, tenant).size()).isEqualTo(2);

        // delete first
        FlowWithSource deleted = flowRepository.delete(first);
        Await.until(() -> count.get() == 1, Duration.ofMillis(10), Duration.ofSeconds(5));
        assertThat(getFlowsForTenant(flowListenersService, tenant).size()).isEqualTo(1);

        // restore must works
        flowRepository.create(GenericFlow.of(first));
        Await.until(() -> count.get() == 2, Duration.ofMillis(10), Duration.ofSeconds(5));
        assertThat(getFlowsForTenant(flowListenersService, tenant).size()).isEqualTo(2);

    }

    public List<FlowWithSource> getFlowsForTenant(FlowListenersInterface flowListenersService, String tenantId){
        return flowListenersService.flows().stream()
            .filter(f -> tenantId.equals(f.getTenantId()))
            .toList();
    }

}
