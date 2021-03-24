package io.kestra.runner.kafka.services;

import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;

import java.util.Properties;

public class KafkaStreamsBuilder extends StreamsBuilder {
    @Override
    public synchronized Topology build() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.TOPOLOGY_OPTIMIZATION, StreamsConfig.OPTIMIZE);

        return super.build(properties);
    }
}
