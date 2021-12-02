package io.kestra.runner.kafka;

import io.kestra.runner.kafka.configs.LoggerConfig;
import io.micronaut.core.annotation.Nullable;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.event.Level;

import java.time.Instant;
import java.util.Map;

@NoArgsConstructor
public class ConsumerInterceptor<K,V> extends AbstractInterceptor implements org.apache.kafka.clients.consumer.ConsumerInterceptor<K, V> {
    @Override
    public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
        records.forEach(record -> this.logRecord(
            LoggerConfig.Type.CONSUMER,
            record.topic(),
            record.partition(),
            record.offset(),
            record.timestamp(),
            record.key(),
            record.value()
        ));

        return records;
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
        offsets.forEach((topicPartition, offsetAndMetadata) -> this.logCommit(
            topicPartition.topic(),
            topicPartition.partition(),
            offsetAndMetadata.offset()
        ));
    }

    protected void logCommit(
        String topic,
        Integer partition,
        @Nullable Long offset
    ) {
        Level level = isMatch(LoggerConfig.Type.CONSUMER_COMMIT, topic);

        if (level == null) {
            return;
        }

        String format = "[{}> {}{}{}]";
        Object[] args = {
            LoggerConfig.Type.CONSUMER_COMMIT,
            topic,
            partition != null ? "[" + partition + "]" : "",
            offset != null ? "@" + offset : "",
        };

        this.log(level, format, args);
    }
}
