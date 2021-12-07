package io.kestra.runner.kafka;

import io.kestra.runner.kafka.configs.LoggerConfig;
import io.micronaut.core.annotation.Nullable;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.event.Level;

import java.time.Instant;

@NoArgsConstructor
public class ProducerInterceptor<K, V> extends AbstractInterceptor implements org.apache.kafka.clients.producer.ProducerInterceptor<K, V> {
    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        this.logRecord(
            LoggerConfig.Type.PRODUCER,
            record.topic(),
            record.partition(),
            null,
            record.timestamp(),
            record.key(),
            record.value()
        );

        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        this.logAck(
            metadata.topic(),
            metadata.partition(),
            metadata.offset(),
            metadata.timestamp()
        );
    }

    protected void logAck(
        String topic,
        Integer partition,
        @Nullable Long offset,
        Long timestamp
    ) {
        Level level = isMatch(LoggerConfig.Type.PRODUCER_ACK, topic);

        if (level == null) {
            return;
        }

        String format = "[{}> {}{}{}{}]";
        Object[] args = {
            LoggerConfig.Type.PRODUCER_ACK,
            topic,
            partition != null ? "[" + partition + "]" : "",
            offset != null ? "@" + offset : "",
            timestamp != null ? " " + Instant.ofEpochMilli(timestamp) : "",
        };

        this.log(level, format, args);
    }
}
