package io.kestra.runner.kafka.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.tasks.debugs.Echo;
import io.kestra.runner.kafka.serializers.JsonSerde;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class SafeKeyValueStoreTest {
    @Inject
    KafkaStreamSourceService kafkaStreamSourceService;

    @Inject
    KafkaAdminService kafkaAdminService;

    @SuppressWarnings("resource")
    @Test
    void test() throws JsonProcessingException {
        Properties properties = new Properties();
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost");
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "unit-test");

        StreamsBuilder builder = new KafkaStreamsBuilder();
        builder
            .globalTable(
                kafkaAdminService.getTopicName(Flow.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Flow.class)).withName("GlobalKTable.Flow"),
                Materialized.<String, Flow, KeyValueStore<Bytes, byte[]>>as("flow")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Flow.class))
            );

        Topology topology = builder.build();

        TopologyTestDriver testTopology = new TopologyTestDriver(topology, properties);
        KeyValueStore<String, String> storeString = testTopology.getKeyValueStore("flow");
        KeyValueStore<String, Flow> storeFlow = testTopology.getKeyValueStore("flow");

        SafeKeyValueStore<String, Flow> safeKeyValueStore = new SafeKeyValueStore<>(testTopology.getKeyValueStore("flow"), "flow");

        Flow validFlow = Flow.builder()
            .id("test")
            .namespace("namespace")
            .revision(1)
            .tasks(List.of(
                Echo.builder().type(Echo.class.getName()).id("test").format("test").build()
            ))
            .build();

        String invalidFlow = JacksonMapper.ofJson().writeValueAsString(Map.of(
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
        ));

        storeFlow.put("validOne1", validFlow);
        storeString.put("notJson", "notJson");
        storeFlow.put("validOne2", validFlow);
        storeString.put("invalidTask1", invalidFlow);
        storeFlow.put("validOne3", validFlow);
        storeString.put("invalidTask2", invalidFlow);

        Optional<Flow> notJson = safeKeyValueStore.get("notJson");
        assertThat(notJson.isEmpty(), is(true));

        Optional<Flow> invalidTask = safeKeyValueStore.get("invalidTask1");
        assertThat(invalidTask.isEmpty(), is(true));

        Optional<Flow> validOne = safeKeyValueStore.get("validOne2");
        assertThat(validOne.isEmpty(), is(false));

        List<Flow> list = safeKeyValueStore.all().filter(flow -> !flow.isDeleted()).collect(Collectors.toList());
        assertThat(list.size(), is(3));
    }
}