package io.kestra.runner.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.runner.kafka.services.KafkaStreamService;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.micronaut.management.endpoint.annotation.Selector;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.apache.kafka.streams.KafkaStreams;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Endpoint(id = "kafkastreams", defaultSensitive = false)
@Requires(property = "kestra.server-type", pattern = "(EXECUTOR|STANDALONE|SCHEDULER)")
@KafkaQueueEnabled
public class KafkaStreamEndpoint implements ApplicationEventListener<KafkaStreamEndpoint.Event>  {
    private Map<String, KafkaStreamService.Stream> streams;

    @Override
    public void onApplicationEvent(KafkaStreamEndpoint.Event event) {
        if (streams == null) {
            streams = new HashMap<>();
        }

        streams.put(event.getClientId(), event.getStream());
    }

    @Read
    public List<KafkaStream> global() {
        return (streams != null ? streams : new HashMap<String, KafkaStreamService.Stream>())
            .entrySet()
            .stream()
            .map(entry -> KafkaStream.of(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
    }

    @Read
    public Object detail(@Selector String clientId, @Selector String type) {
        if (!streams.containsKey(clientId)) {
            throw new IllegalArgumentException("Invalid clientId with name '" +  clientId + "'");
        }

        KafkaStreamService.Stream stream = this.streams.get(clientId);

        switch (type) {
            case "lag":
                return stream
                    .allLocalStorePartitionLags()
                    .entrySet()
                    .stream()
                    .flatMap(e -> e.getValue()
                        .entrySet()
                        .stream()
                        .map(f -> LagInfo.of(
                            e.getKey(),
                            f.getKey(),
                            f.getValue()
                        ))
                    )
                    .collect(Collectors.toList());
            case "metrics":
                return stream
                    .metrics()
                    .values()
                    .stream()
                    .map(Metric::of)
                    .collect(Collectors.toList());
            default:
                throw new IllegalArgumentException("Invalid type '" +  type + "'");
        }
    }

    @Getter
    @Builder
    @JsonInclude
    public static class KafkaStream {
        Boolean ready;
        String clientId;
        KafkaStreams.State state;
        @JsonInclude
        Map<String, Long> storeLags;

        public static KafkaStream of(String clientId, KafkaStreamService.Stream stream) {
            return KafkaStream.builder()
                .ready(true)
                .clientId(clientId)
                .state(stream.state())
                .storeLags(stream
                    .allLocalStorePartitionLags()
                    .entrySet()
                    .stream()
                    .flatMap(e -> e.getValue()
                        .entrySet()
                        .stream()
                        .map(f -> LagInfo.of(
                            e.getKey(),
                            f.getKey(),
                            f.getValue()
                        ))
                    )
                    .collect(Collectors.groupingBy(LagInfo::getStore, Collectors.toList()))
                    .entrySet()
                    .stream()
                    .map(entry -> new AbstractMap.SimpleEntry<>(
                        entry.getKey(),
                        entry.getValue()
                            .stream()
                            .mapToLong(LagInfo::getOffsetLag)
                            .sum()
                    ))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                )
                .build();
        }
    }

    @Getter
    @Builder
    @JsonInclude
    public static class LagInfo {
        private String store;
        private Integer partition;
        private Long currentOffsetPosition;
        private Long endOffsetPosition;
        private Long offsetLag;

        public static LagInfo of(String topic, Integer partition, org.apache.kafka.streams.LagInfo lagInfo) {
            return LagInfo.builder()
                .store(topic)
                .partition(partition)
                .currentOffsetPosition(lagInfo.currentOffsetPosition())
                .endOffsetPosition(lagInfo.endOffsetPosition())
                .offsetLag(lagInfo.offsetLag())
                .build();
        }
    }

    @Getter
    @Builder
    public static class Metric {
        private String group;
        private String name;
        private Map<String, String> tags;
        private Object value;

        public static <T extends org.apache.kafka.common.Metric> Metric of(T metric) {
            return Metric.builder()
                .group(metric.metricName().group())
                .name(metric.metricName().name())
                .tags(metric.metricName().tags())
                .value(metric.metricValue())
                .build();
        }
    }

    @Getter
    @AllArgsConstructor
    public static class Event {
        String clientId;
        KafkaStreamService.Stream stream;
    }
}
