package io.kestra.runner.kafka.services;

import com.google.common.base.CaseFormat;
import io.micronaut.context.annotation.Value;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;

@Singleton
@Slf4j
public class KafkaConfigService {
   @Value("${kestra.kafka.defaults.consumer-prefix:kestra}")
   private String consumerPrefix;

    public String getConsumerGroupName(Class<?> group) {
        return this.consumerPrefix + "_" +
            CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE,
                group.getSimpleName().replace("Kafka", "")
            );
    }
}
