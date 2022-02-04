package io.kestra.runner.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.FlowListenersTest;
import io.kestra.core.runners.StandAloneRunner;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.runner.kafka.configs.TopicsConfig;
import io.kestra.runner.kafka.services.KafkaProducerService;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class KafkaFlowListenersTest extends FlowListenersTest {
    @Inject
    ApplicationContext applicationContext;

    @Inject
    KafkaFlowListeners flowListenersService;

    @Inject
    KafkaProducerService kafkaProducerService;

    @Inject
    private StandAloneRunner runner;

    @BeforeEach
    private void init() {
        runner.setSchedulerEnabled(false);
        runner.run();
    }

    @Test
    public void all() {
        this.suite(flowListenersService);
    }

    @Test
    public void invalidFlows() throws JsonProcessingException, InterruptedException {
        TopicsConfig topicsConfig = KafkaQueue.topicsConfig(applicationContext, Flow.class);

        KafkaProducerService.Producer<String> producer = kafkaProducerService.of(
            KafkaFlowListenersTest.class,
            Serdes.String()
        );

        producer.send(new ProducerRecord<>(
            topicsConfig.getName(),
            "",
            JacksonMapper.ofJson().writeValueAsString(Map.of(
                "id", "invalid",
                "namespace", "io.kestra.unittest",
                "revision", 1,
                "tasks", List.of(
                    Map.of(
                        "id", "invalid",
                        "type", "io.kestra.core.tasks.debugs.Echo",
                        "level", "invalid"
                    )
                )
            ))
        ));

        producer.send(new ProducerRecord<>(
            topicsConfig.getName(),
            "",
            JacksonMapper.ofJson().writeValueAsString(Map.of(
                "id", "invalid",
                "namespace", "io.kestra.unittest",
                "revision", 1,
                "tasks", List.of(
                    Map.of(
                        "id", "invalid",
                        "type", "io.kestra.core.tasks.debugs.Invalid",
                        "level", "invalid"
                    )
                )
            ))
        ));

        String flowId = IdUtils.create();

        Flow flow = create(flowId, IdUtils.create());
        flowRepository.create(flow);

        CountDownLatch countDownLatch = new CountDownLatch(1);
        flowListenersService.listen(flows -> {
            if (flows.stream().anyMatch(f -> f.getId().equals(flowId))) {
                countDownLatch.countDown();
            }
        });

        countDownLatch.await(1, TimeUnit.MINUTES);

        assertThat(countDownLatch.getCount(), is(0L));
    }
}