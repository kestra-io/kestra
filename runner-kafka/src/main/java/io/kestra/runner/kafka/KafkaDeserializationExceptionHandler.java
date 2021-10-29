package io.kestra.runner.kafka;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.templates.Template;
import io.kestra.runner.kafka.configs.TopicsConfig;
import io.kestra.runner.kafka.services.KafkaStreamService;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.processor.ProcessorContext;

import java.util.Map;

@Slf4j
public class KafkaDeserializationExceptionHandler implements DeserializationExceptionHandler {
    private ApplicationContext applicationContext;

    @Override
    public void configure(Map<String, ?> configs) {
        applicationContext = (ApplicationContext) configs.get(KafkaStreamService.APPLICATION_CONTEXT_CONFIG);
    }

    @Override
    public DeserializationHandlerResponse handle(ProcessorContext context, ConsumerRecord<byte[], byte[]> record, Exception exception) {
        String message = "Exception caught during deserialization, stream will continue! applicationId: {}, taskId: {}, topic: {}, partition: {}, offset: {}";
        Object[] args = {
            context.applicationId(),
            context.taskId(),
            record.topic(),
            record.partition(),
            record.offset(),
            exception
        };

        TopicsConfig topicsConfig = KafkaQueue.topicsConfigByTopicName(applicationContext, record.topic());

        if (topicsConfig.getCls() == Flow.class || topicsConfig.getCls() == Template.class) {
            if (log.isDebugEnabled()) {
                log.debug(message, args);
            }
        } else {
            log.warn(message, args);
        }

        return DeserializationHandlerResponse.CONTINUE;
    }
}
